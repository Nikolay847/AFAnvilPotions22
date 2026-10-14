# AFAnvilPotions (stack-compatible fork)

Fork adapted for Paper 1.21.4 / Java 21 and custom stack sizes provided through Paper Data Components.

## Build
Requires JDK 21 and Maven 3.9+.

```bash
mvn clean package
```

The plugin JAR is produced in `target/AFAnvilPotions-1.5.0.jar`.

## Install
1. Build this project on Java 21.
2. Put the resulting JAR in the server's `plugins` directory.
3. Keep CakePotionStack enabled and configure the same item stack limits there.
4. Restart the Paper server.

The existing `config.yml` format and `/afanvilpotions reload` command are retained.

## Stack transaction safety
- The output amount is the minimum amount available in both input slots.
- The same number of items is consumed from both input slots.
- Result taking is handled as a single cancelled-and-replaced transaction rather than storing leftover stacks in a mutable-ItemStack map.
- Shift-click checks inventory capacity before consuming inputs.
- XP cost is multiplied by the output amount, matching the original plugin's per-potion price behavior.
- The output retains the input's Paper `MAX_STACK_SIZE` data component.
