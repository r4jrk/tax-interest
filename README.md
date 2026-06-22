# tax-interest (Odsetki podatkowe)

Calculates the penalty interest owed on a late tax payment, spanning multiple statutory rate
periods, and rolls weekend/holiday dates forward to the next NBP business day. Part of the
[r4_tech tools](../README.md) suite.

## Run

```bash
mvn -DskipTests install            # once (from the repo root)
mvn -pl tax-interest javafx:run
```

## Package

```bash
mvn -pl tax-interest -am -Pinstaller -DskipTests package
# -> target/installer/r4_tech Odsetki podatkowe/
```

## Data files

Needs `stopy.csv` — the interest-rate periods, columns `okresOd,okresDo,stopa` (ISO dates; an empty
`okresDo` marks the still-open current period). A copy ships at the module root. For a packaged app,
put it next to the executable or set `-Dr4tech.dataDir=<dir>`. See the
[root README](../README.md#data-files-csv) for the full lookup order.
