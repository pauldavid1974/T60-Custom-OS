# Backup, flashing and recovery — NOT READY

**There is no demonstrated safe flash/rollback procedure for this T60.** Do not treat generic Realtek SDK programming instructions as a recovery plan for this board.

Before any device write, document and independently verify:

- Exact SoC, board and flash layout; expected image format and signature checks.
- Proven means of extracting or obtaining the *exact* currently installed factory image, including calibration/vendor partitions if relevant.
- Integrity verification (cryptographic hashes), securely stored backup and a practical restoration rehearsal where possible.
- Accessible hardware recovery interface and confirmed compatible tools, without undocumented pin assumptions.
- Power/battery safety, interruption handling, precise commands and a stop/rollback plan.
- Paul's informed approval for the specific operation and risks.

Neither RTL8762C nor RTL8762D firmware files found bundled in GloryFit have been verified for this watch. **Do not flash them.**

No `flash`, `dfu`, `reset`, `unlock`, `erase`, or device-write automation should be introduced until the prerequisites are met and reviewed by Paul.
