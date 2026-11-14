package app.kash

/** The name of the application, as macOS shows it in the menu bar, the Dock and the application switcher. */
internal const val APP_NAME = "KaSH"

/**
 * Makes [APP_NAME] the name of the application in the menu bar of macOS (the menu next to the apple, with About and Quit), as KaTTY does. An
 * application that is packaged has the name of its bundle, but one that is started by Gradle or from a jar has the name of its main class,
 * `MainKt`. AWT reads this property when it starts, so it has to be set before anything uses it: it is the first thing that `main` does.
 *
 * The application switcher (⌘Tab) shows the process's name instead, `java`: `-Xdock:name`, a JVM option that `app/build.gradle.kts` gives, names it.
 */
internal fun nameApplication() {
    System.setProperty("apple.awt.application.name", APP_NAME)
}
