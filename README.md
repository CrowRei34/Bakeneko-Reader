# Bakeneko-Reader

Bakeneko-Reader is a premium, open-source manga reader for Desktop and Android.

## Features
- Offline reading and downloads
- Clean and modern Material UI
- Track your favorites and history
- Support for multiple manga sources

## Building
To build and run the desktop application, ensure you have Java 21+ installed and run:

```bash
./gradlew :desktop:run
```

To build a Linux AppImage or Deb package:

```bash
./gradlew :desktop:packageDistributionForCurrentOS
./gradlew :desktop:packageAppImage
```

## License
Licensed under the GNU GPLv3 License.
