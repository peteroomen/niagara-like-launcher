# AOSP LatinIME native engine

The dictionary and suggestion engine from Android's open-source keyboard, copied from
`platform/packages/inputmethods/LatinIME` (`native/jni`) at commit
`127336e9f29d69607eab55982324b210279ae8c5` (26 February 2025). Apache License 2.0: see `NOTICE`.
`src/main/assets/main_en.dict` is the English dictionary from the same commit
(`java/res/raw/main_en.dict`).

Changes from AOSP, all marked `Glass Keyboard`:

- Each `com_android_*.cpp` registers its native methods on this app's classes in
  `io.github.peteroomen.keyboard.engine` instead of AOSP's.
- `updateEntriesForInputEventsNative` isn't registered: it needs AOSP's
  `WordInputEventForPersonalization` class, and the keyboard doesn't use it.

Nothing else in the engine is changed, so it can be updated by copying a newer `native/jni`
over it and making the same changes.
