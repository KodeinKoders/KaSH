// What KaSH asks of macOS that Java has no API for: the notifications of the Notification Center, the login item of the application, and the
// panels that do not activate KaSH when they are clicked (its own notifications' window).
// Called from Kotlin through the FFM API (app.kash.macos): plain C functions, strings in UTF-8.

#import <AppKit/AppKit.h>
#import <Foundation/Foundation.h>
#import <objc/message.h>
#import <ServiceManagement/ServiceManagement.h>
#import <UserNotifications/UserNotifications.h>

typedef void (*kash_click_callback)(const char *identifier);

static kash_click_callback clickCallback = NULL;

// The categories of the notifications of a conversation that KaSH can mark as read (its plugin's own way, or by opening it), and their action:
// a button of the notification, titled as it is done.
static NSString *const KashConversationCategory = @"conversation";
static NSString *const KashConversationOpeningCategory = @"conversation-opening";
static NSString *const KashMarkReadAction = @"markRead";

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
    } else if (clickCallback != NULL && [response.actionIdentifier isEqualToString:KashMarkReadAction]) {
        // Its button "Mark as read": the callback is given the identifier after `mark-read:`.
        clickCallback([@"mark-read:" stringByAppendingString:response.notification.request.identifier].UTF8String);
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

// Whether the user lets KaSH show notifications: 0 not asked yet, 1 denied, 2 allowed (also provisionally), -1 outside an application bundle or
// when macOS did not answer in 2 s.
int kash_notifications_authorization(void) {
    if (!kash_in_app_bundle()) return -1;
    @autoreleasepool {
        __block int result = -1;
        dispatch_semaphore_t answered = dispatch_semaphore_create(0);
        [UNUserNotificationCenter.currentNotificationCenter getNotificationSettingsWithCompletionHandler:^(UNNotificationSettings *settings) {
            switch (settings.authorizationStatus) {
                case UNAuthorizationStatusNotDetermined: result = 0; break;
                case UNAuthorizationStatusDenied: result = 1; break;
                default: result = 2; break;
            }
            dispatch_semaphore_signal(answered);
        }];
        dispatch_semaphore_wait(answered, dispatch_time(DISPATCH_TIME_NOW, 2 * NSEC_PER_SEC));
        return result;
    }
}

// How macOS shows KaSH's notifications, as the user set it in System Settings › Notifications › KaSH: 0 not at all, 1 banners (which go away), 2
// alerts (which stay until they are closed), -1 outside an application bundle or when macOS did not answer in 2 s. KaSH cannot choose it.
int kash_notifications_alert_style(void) {
    if (!kash_in_app_bundle()) return -1;
    @autoreleasepool {
        __block int result = -1;
        dispatch_semaphore_t answered = dispatch_semaphore_create(0);
        [UNUserNotificationCenter.currentNotificationCenter getNotificationSettingsWithCompletionHandler:^(UNNotificationSettings *settings) {
            switch (settings.alertStyle) {
                case UNAlertStyleNone: result = 0; break;
                case UNAlertStyleBanner: result = 1; break;
                case UNAlertStyleAlert: result = 2; break;
            }
            dispatch_semaphore_signal(answered);
        }];
        dispatch_semaphore_wait(answered, dispatch_time(DISPATCH_TIME_NOW, 2 * NSEC_PER_SEC));
        return result;
    }
}

// Makes KaSH's panels titled [title] (AWT's windows of type UTILITY, which are NSPanels) not activate KaSH when they are clicked, as macOS's
// notifications do not activate the application in front: AWT has no window of that kind (its POPUP windows are normal ones). Both the style
// and the window server's flag that it sets when a panel is created with it (`_setPreventsActivation:`, when the panel has it): the style
// alone, set after the panel was created, may not be read again. On AppKit's thread, later.
void kash_panel_make_nonactivating(const char *title) {
    // Called on Swing's thread, which has no autorelease pool of its own: the string made here is released with this one.
    @autoreleasepool {
        NSString *name = [NSString stringWithUTF8String:title];
        dispatch_async(dispatch_get_main_queue(), ^{
            for (NSWindow *window in NSApp.windows) {
                if (![window isKindOfClass:NSPanel.class] || ![window.title isEqualToString:name]) continue;
                window.styleMask |= NSWindowStyleMaskNonactivatingPanel;
                ((NSPanel *)window).hidesOnDeactivate = NO;
                SEL prevents = NSSelectorFromString(@"_setPreventsActivation:");
                if ([window respondsToSelector:prevents]) ((void (*)(id, SEL, BOOL))objc_msgSend)(window, prevents, YES);
            }
        });
    }
}

// A category of the notifications of a conversation, whose button "Mark as read" is titled [title].
static UNNotificationCategory *kash_mark_read_category(NSString *identifier, const char *title) {
    UNNotificationAction *action = [UNNotificationAction actionWithIdentifier:KashMarkReadAction
                                                                        title:[NSString stringWithUTF8String:title]
                                                                      options:UNNotificationActionOptionNone];
    return [UNNotificationCategory categoryWithIdentifier:identifier actions:@[action] intentIdentifiers:@[] options:UNNotificationCategoryOptionNone];
}

// The buttons "Mark as read" of the notifications of a conversation, titled in KaSH's language: [direct] its plugin's own way, [byOpening] by
// opening it. Their categories, which the Notification Center keeps; again when the language changes.
void kash_notifications_set_mark_read_titles(const char *direct, const char *byOpening) {
    if (delegate == nil) return;
    @autoreleasepool {
        NSSet *categories = [NSSet setWithObjects:kash_mark_read_category(KashConversationCategory, direct),
                                                  kash_mark_read_category(KashConversationOpeningCategory, byOpening), nil];
        [UNUserNotificationCenter.currentNotificationCenter setNotificationCategories:categories];
    }
}

// Shows a notification, which the click callback is given [identifier] of when it is clicked; with the button "Mark as read" when [markRead] is
// 1 (its plugin's own way) or 2 (by opening the conversation), none for 0.
void kash_notifications_post(const char *identifier, const char *title, const char *subtitle, const char *body, int sound, int markRead) {
    if (delegate == nil) return;
    @autoreleasepool {
        UNMutableNotificationContent *content = [UNMutableNotificationContent new];
        content.title = [NSString stringWithUTF8String:title];
        if (subtitle != NULL && subtitle[0] != 0) content.subtitle = [NSString stringWithUTF8String:subtitle];
        if (body != NULL && body[0] != 0) content.body = [NSString stringWithUTF8String:body];
        if (sound) content.sound = UNNotificationSound.defaultSound;
        if (markRead == 1) content.categoryIdentifier = KashConversationCategory;
        if (markRead == 2) content.categoryIdentifier = KashConversationOpeningCategory;
        UNNotificationRequest *request = [UNNotificationRequest requestWithIdentifier:[NSString stringWithUTF8String:identifier]
                                                                              content:content
                                                                              trigger:nil];
        [UNUserNotificationCenter.currentNotificationCenter addNotificationRequest:request withCompletionHandler:^(NSError *error) {
            if (error != nil) NSLog(@"KaSH: notification not shown: %@", error);
        }];
    }
}

// Takes the notification [identifier] away, from the screen and from the Notification Center's list: what it says was read.
void kash_notifications_remove(const char *identifier) {
    if (delegate == nil) return;
    @autoreleasepool {
        [UNUserNotificationCenter.currentNotificationCenter removeDeliveredNotificationsWithIdentifiers:@[[NSString stringWithUTF8String:identifier]]];
    }
}

// The identifiers of KaSH's notifications that the Notification Center still shows (those of the last launches too), separated by U+001F, in
// [buffer] of [size] bytes, as a C string. The length that they need (without the final 0), which is more than [size] when they did not fit (none
// are written then); -1 when macOS did not answer in 2 s.
int kash_notifications_delivered(char *buffer, int size) {
    if (delegate == nil) return -1;
    @autoreleasepool {
        __block NSString *joined = nil;
        dispatch_semaphore_t answered = dispatch_semaphore_create(0);
        [UNUserNotificationCenter.currentNotificationCenter getDeliveredNotificationsWithCompletionHandler:^(NSArray<UNNotification *> *notifications) {
            NSMutableArray<NSString *> *identifiers = [NSMutableArray arrayWithCapacity:notifications.count];
            for (UNNotification *notification in notifications) [identifiers addObject:notification.request.identifier];
            joined = [identifiers componentsJoinedByString:@"\x1F"];
            dispatch_semaphore_signal(answered);
        }];
        if (dispatch_semaphore_wait(answered, dispatch_time(DISPATCH_TIME_NOW, 2 * NSEC_PER_SEC)) != 0) return -1;
        const char *utf8 = joined.UTF8String;
        int length = (int) strlen(utf8);
        if (length < size) memcpy(buffer, utf8, length + 1);
        else if (size > 0) buffer[0] = 0;
        return length;
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
