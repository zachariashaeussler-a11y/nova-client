NOVA Client - Fabric mod for Minecraft 1.21.11

EASIEST: build online with GitHub (no installs)
 1. Make a free GitHub account, create a new repository.
 2. Upload ALL files from this folder (keep the .github folder).
 3. Open the Actions tab -> wait for "build" to finish (about 3-5 minutes).
 4. Open that run -> download the artifact "novaclient-jar" -> unzip -> novaclient-1.0.0.jar

INSTALL
 1. Install Fabric Loader for 1.21.11 (fabricmc.net/use) and put Fabric API in .minecraft/mods
 2. Put novaclient-1.0.0.jar in .minecraft/mods
 3. Launch the Fabric 1.21.11 profile. In game press Right Shift for the NOVA menu.
 Optional but recommended for FPS: Sodium + Lithium.

BUILD LOCALLY instead: JDK 21 + Gradle 8.14+, then:  gradle build   (jar in build/libs/)
If a version in gradle.properties is not found, copy current ones from fabricmc.net/develop.
