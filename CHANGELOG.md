# Changelog

All notable changes to this project will be documented in this file.
The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/).

## [2.1.0] - 2025-MM-DD

### Added

- Add add one-step destination choice model for free activities [[Link]](https://github.com/sekilab/Pseudo-PFLOW/blob/e7910843bc16a8e9d7ae550885418b161ff50711/src/pseudo/gen/ActGenerator.java#L303-L354)
- Add a Softmax function to destination choice model [[Link]](https://github.com/sekilab/Pseudo-PFLOW/blob/e7910843bc16a8e9d7ae550885418b161ff50711/src/utils/Softmax.java#L19-L41)

### Changed

- Adjust factor of distance in Huff model for destination choice, and add time cost factors
- Change non-commuter activity generation from MNL model to Huff model
- Redefine the attraction for destination choice

### Fixed

- Fixed empty parameter set for students with free activity logics ([#1](https://github.com))

### Deprecated

### Removed

## [2.0.0] - 2025-04-01
