// What KaSH asks of macOS that Java has no API for: the notifications of the Notification Center, and the login item of the application.
// Called from Kotlin through the FFM API (app.kash.macos): plain C functions, strings in UTF-8.

#import <Foundation/Foundation.h>
#import <ServiceManagement/ServiceManagement.h>
#import <UserNotifications/UserNotifications.h>

typedef void (*kash_click_callback)(const char *identifier);

static kash_click_callback clickCallback = NULL;

// The Notification Center calls its delegate on a thread of its own: the callback is an FFM upcall, which any thread can call.
@interface KashNotificationDelegate : NSObject <UNUserNotificationCenterDelegate>
@end

@implementation KashNotificationDelegate
// Shown even while KaSH is the active application: a service that is not the one shown still has to say that a message came.
- (void)userNotificationCenter:(UNUserNotificationCenter *)center
       willPresentNotification:(UNNotification *)notification
         withCompletionHandler:(void (^)(UNNotificationPresentationOptions))completionHandler {
    completionHandler(UNNotificationPresentationOptionBanner | UNNotificationPresentationOptionList | UNNotificationPresentationOptionSound);
}

- (void)userNotificationCenter:(UNUserNotificationCenter *)center
didReceiveNotificationResponse:(UNNotificationResponse *)response
         withCompletionHandler:(void (^)(void))completionHandler {
    if (clickCallback != NULL && [response.actionIdentifier isEqualToString:UNNotificationDefaultActionIdentifier]) {
        clickCallback(response.notification.request.identifier.UTF8String);
    }
    completionHandler();
}
@end

static KashNotificationDelegate *delegate = nil;

// 1 when the process is KaSH.app, the application bundle (`app.kash`): the Notification Center and the login items only know bundles, and
// UNUserNotificationCenter throws in a process that is not one. The bundle that the run task starts the JVM from (`app.kash.dev`, so that macOS
// names it KaSH) is not the application: it has no signature of its own, and takes the fallbacks of a development run.
int kash_in_app_bundle(void) {
    @autoreleasepool {
        NSBundle *bundle = NSBundle.mainBundle;
        return [bundle.bundleIdentifier isEqualToString:@"app.kash"] && [bundle.bundlePath hasSuffix:@".app"] ? 1 : 0;
    }
}

// Becomes the delegate of the Notification Center and asks for the authorization (macOS asks the user once). 0 outside an application bundle.
int kash_notifications_start(kash_click_callback callback) {
    if (!kash_in_app_bundle()) return 0;
    @autoreleasepool {
        clickCallback = callback;
        UNUserNotificationCenter *center = UNUserNotificationCenter.currentNotificationCenter;
        delegate = [KashNotificationDelegate new];
        center.delegate = delegate;
        [center requestAuthorizationWithOptions:(UNAuthorizationOptionAlert | UNAuthorizationOptionSound | UNAuthorizationOptionBadge)
                              completionHandler:^(BOOL granted, NSError *error) {
            if (error != nil) NSLog(@"KaSH: notifications not authorized: %@", error);
        }];
        return 1;
    }
}

// Shows a notification, which the click callback is given [identifier] of when it is clicked.
void kash_notifications_post(const char *identifier, const char *title, const char *subtitle, const char *body, int sound) {
    if (delegate == nil) return;
    @autoreleasepool {
        UNMutableNotificationContent *content = [UNMutableNotificationContent new];
        content.title = [NSString stringWithUTF8String:title];
        if (subtitle != NULL && subtitle[0] != 0) content.subtitle = [NSString stringWithUTF8String:subtitle];
        if (body != NULL && body[0] != 0) content.body = [NSString stringWithUTF8String:body];
        if (sound) content.sound = UNNotificationSound.defaultSound;
        UNNotificationRequest *request = [UNNotificationRequest requestWithIdentifier:[NSString stringWithUTF8String:identifier]
                                                                              content:content
                                                                              trigger:nil];
        [UNUserNotificationCenter.currentNotificationCenter addNotificationRequest:request withCompletionHandler:^(NSError *error) {
            if (error != nil) NSLog(@"KaSH: notification not shown: %@", error);
        }];
    }
}

// The status of the application's login item (SMAppServiceStatus): 0 not registered, 1 enabled, 2 waiting for the user's approval in the System
// Settings, 3 not found. -1 outside an application bundle.
int kash_login_item_status(void) {
    if (!kash_in_app_bundle()) return -1;
    @autoreleasepool {
        return (int) SMAppService.mainAppService.status;
    }
}

// Registers or unregisters the application as a login item. 1 when it worked; the error is logged otherwise.
int kash_login_item_set(int enabled) {
    if (!kash_in_app_bundle()) return 0;
    @autoreleasepool {
        NSError *error = nil;
        BOOL done = enabled ? [SMAppService.mainAppService registerAndReturnError:&error]
                            : [SMAppService.mainAppService unregisterAndReturnError:&error];
        if (!done) NSLog(@"KaSH: login item not %@: %@", enabled ? @"registered" : @"unregistered", error);
        return done ? 1 : 0;
    }
}
