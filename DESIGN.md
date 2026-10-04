---
name: FlightFilters
description: A personal fare radar drawn as a midnight metro map, one enamel ink per departure line.
colors:
  midnight: "#0E1A3A"
  midnight-lowest: "#0A1430"
  midnight-low: "#122147"
  midnight-mid: "#172853"
  midnight-high: "#1D305F"
  midnight-highest: "#243A6E"
  porcelain: "#F4F1E8"
  porcelain-dim: "#B8C0D8"
  seam: "#34477A"
  seam-strong: "#5C6E9E"
  plate-lowest: "#FAF8F2"
  plate-low: "#EDE9DD"
  plate-mid: "#E6E1D3"
  plate-high: "#DED8C8"
  plate-highest: "#D5CEBC"
  ink-dim: "#3F4A6C"
  plate-outline: "#7C8299"
  plate-seam: "#C6C2B4"
  line-scarlet: "#E2382B"
  line-cobalt: "#3B7BE8"
  line-amber: "#F2A900"
  line-green: "#2E9E57"
  line-violet: "#A06AE0"
  line-teal: "#22A9A9"
  line-rose: "#E56FA3"
  line-olive: "#B7B03A"
  signal-green: "#3DB46A"
  print-scarlet: "#CC2A1E"
  print-cobalt: "#1F5CC4"
  print-amber: "#B57A00"
  print-green: "#1F7F45"
  print-violet: "#7A45BF"
  print-teal: "#13807F"
  print-rose: "#C24B80"
  print-olive: "#7F7A1E"
  print-amber-deep: "#8A5D00"
  midnight-error: "#FF7A6E"
  plate-error: "#B3261E"
typography:
  headline:
    fontFamily: "Fira Sans, sans-serif"
    fontSize: "22sp"
    fontWeight: 600
    lineHeight: "28sp"
    letterSpacing: "0sp"
    fontFeature: "tnum, lnum"
  title-large:
    fontFamily: "Fira Sans, sans-serif"
    fontSize: "20sp"
    fontWeight: 600
    lineHeight: "26sp"
    letterSpacing: "0sp"
    fontFeature: "tnum, lnum"
  title:
    fontFamily: "Fira Sans, sans-serif"
    fontSize: "16sp"
    fontWeight: 600
    lineHeight: "22sp"
    letterSpacing: "0.1sp"
    fontFeature: "tnum, lnum"
  title-small:
    fontFamily: "Fira Sans, sans-serif"
    fontSize: "14sp"
    fontWeight: 600
    lineHeight: "20sp"
    letterSpacing: "0.1sp"
    fontFeature: "tnum, lnum"
  body-large:
    fontFamily: "Fira Sans, sans-serif"
    fontSize: "16sp"
    fontWeight: 400
    lineHeight: "24sp"
    letterSpacing: "0sp"
    fontFeature: "tnum, lnum"
  body:
    fontFamily: "Fira Sans, sans-serif"
    fontSize: "14sp"
    fontWeight: 400
    lineHeight: "20sp"
    letterSpacing: "0sp"
    fontFeature: "tnum, lnum"
  body-small:
    fontFamily: "Fira Sans, sans-serif"
    fontSize: "12sp"
    fontWeight: 400
    lineHeight: "16sp"
    letterSpacing: "0.1sp"
    fontFeature: "tnum, lnum"
  label-large:
    fontFamily: "Fira Sans, sans-serif"
    fontSize: "14sp"
    fontWeight: 600
    lineHeight: "20sp"
    letterSpacing: "0.2sp"
    fontFeature: "tnum, lnum"
  label:
    fontFamily: "Fira Sans, sans-serif"
    fontSize: "12sp"
    fontWeight: 600
    lineHeight: "16sp"
    letterSpacing: "0.4sp"
    fontFeature: "tnum, lnum"
  label-small:
    fontFamily: "Fira Sans, sans-serif"
    fontSize: "11sp"
    fontWeight: 500
    lineHeight: "14sp"
    letterSpacing: "0.5sp"
    fontFeature: "tnum, lnum"
rounded:
  xs: "3dp"
  sm: "4dp"
  md: "6dp"
  lg: "8dp"
  xl: "12dp"
spacing:
  xs: "4dp"
  sm: "8dp"
  md: "12dp"
  lg: "16dp"
  xl: "32dp"
components:
  line-roundel:
    backgroundColor: "{colors.line-amber}"
    textColor: "{colors.midnight}"
    typography: "{typography.label}"
    rounded: "{rounded.sm}"
    padding: "2dp 6dp"
  line-roundel-large:
    backgroundColor: "{colors.line-amber}"
    textColor: "{colors.midnight}"
    typography: "{typography.title}"
    rounded: "{rounded.sm}"
    padding: "5dp 10dp"
  service-notice:
    backgroundColor: "{colors.midnight-mid}"
    textColor: "{colors.porcelain}"
    typography: "{typography.title}"
    rounded: "{rounded.md}"
    padding: "12dp 14dp"
  itinerary-strip:
    backgroundColor: "{colors.midnight}"
    textColor: "{colors.porcelain}"
    typography: "{typography.title}"
    padding: "12dp 16dp"
  itinerary-strip-selected:
    backgroundColor: "{colors.midnight-high}"
    textColor: "{colors.porcelain}"
  day-cell:
    backgroundColor: "{colors.midnight-high}"
    textColor: "{colors.porcelain}"
    rounded: "{rounded.sm}"
    width: "56dp"
    padding: "8dp 0"
  day-cell-cheapest:
    backgroundColor: "{colors.porcelain}"
    textColor: "{colors.midnight}"
    rounded: "{rounded.sm}"
  sample-stamp:
    backgroundColor: "{colors.line-amber}"
    textColor: "{colors.midnight}"
    typography: "{typography.label-small}"
    rounded: "{rounded.xs}"
    padding: "2dp 6dp"
  live-result:
    backgroundColor: "{colors.midnight-highest}"
    textColor: "{colors.porcelain}"
    rounded: "{rounded.sm}"
    padding: "12dp"
  button-tonal:
    height: "48dp"
  section-title:
    textColor: "{colors.porcelain}"
    typography: "{typography.title}"
    padding: "28dp 16dp 10dp 16dp"
---

# Design System: FlightFilters

## Overview

**Creative North Star: "The Midnight Route Diagram"**

Fares are a metro network fired in enamel. Each departure airport is a coloured line, each change is an interchange circle and each destination is a terminus ring. The ground is a midnight-blue enamel plate, station ticks and type are porcelain, and every line carries its own ink. The chosen line is traced at full ink and everything else recedes to a faint underlay, so the answer is always the brightest thing on screen. Porcelain mode is the same diagram printed on a white enamel plate for daylight. It is a re-print of the same world with its own darker inks, not an inverted palette.

Density follows a transit board. Rows are tight and full-bleed, with 16dp gutters, and data is set in tabular figures so fares and times line up in columns. Depth comes from tonal enamel steps, not shadows. The one piece of decoration is the network grammar itself: lines, ticks, rings and roundels. The same grammar also does the work. It appears in the diagram, in each itinerary strip, in the empty state and in the launcher icon.

The build refuses the category default of a white card list with a blue accent and a chip row. It uses a line diagram instead.

**Key Characteristics:**
- Midnight enamel ground, porcelain type and ticks, one ink per departure line.
- Lines bend only at 90° and 45°. Changes are hollow interchange circles and termini are thick rings.
- The selected line is traced at full ink. Unselected lines drop to 32% alpha.
- Fira Sans throughout, with tabular and lining figures on every style.
- Flat tonal enamel steps with no shadows. Plates have small pressed corners.
- Every fare carries its source and age.

## Colors

The palette is a midnight enamel ground with porcelain type, plus a fixed rack of eight line inks that carry all the colour.

### Primary
- **Porcelain** (porcelain): primary in Midnight. It is used for type, station ticks, interchange strokes, the selected terminus ring and the cheapest-day cell. In Porcelain mode the roles swap, so **Midnight Enamel** (midnight) becomes the ink and porcelain becomes the plate.

### Secondary
- **Signal Amber** (line-amber in Midnight, print-amber-deep in Porcelain): the theme's secondary. It marks attention rather than brand: the SAMPLE DATA stamp, the 2dp border on the selected day cell, and the "outside your window" metadata line. Amber is also the third line ink, so it is a line colour first and a warning colour second.

### Tertiary
- **Signal Green** (signal-green in Midnight, print-green in Porcelain): the theme's tertiary. Service notices use it for "faster" and the live check uses it for a price drop.

### Line Inks
- **The rack** (line-scarlet, line-cobalt, line-amber, line-green, line-violet, line-teal, line-rose, line-olive): inks are assigned by each origin's index in the query, and index 0 (scarlet) is always home. Porcelain mode uses the darker print-* inks in the same order. A line keeps its ink everywhere: in the diagram, the roundel, the itinerary strip and the scanning pulse.
- **Notice inks**: cheaper date uses amber, other origin uses cobalt and faster uses green. A live price rise uses scarlet and a fall uses green.

### Neutral
- **Midnight tonal steps** (midnight-lowest to midnight-highest): surface containers in Midnight mode. Mid is used for service notices, high for a selected itinerary row and an in-window day cell, and highest for the live result panel.
- **Plate tonal steps** (plate-lowest to plate-highest): the same roles in Porcelain mode.
- **Porcelain Dim / Ink Dim** (porcelain-dim, ink-dim): secondary text, destination codes, metadata and source stamps.
- **Seam / Seam Strong** (seam, seam-strong; plate-seam, plate-outline in Porcelain): dividers and outlines. Seam Strong is also the ink for unselected terminus rings and the empty-state line.

### Named Rules
**The One Ink Per Line Rule.** An origin's ink comes from its position in the query, and that ink follows the origin to every surface. Never colour a line by price, airline or status.

**The Roundel Contrast Rule.** A roundel that carries a code first tries porcelain text, then midnight text, on the line's ink. If neither reaches 4.5:1, the plate is darkened or lightened in 5% steps until one does, and it keeps whichever version stays closest to the ink. Line inks are tuned for strokes, and the roundel adjusts them only as far as legibility requires.

**The Brightest Line Rule.** Only the selected itinerary gets full ink, a 7dp stroke and porcelain interchange rings. Every other line drops to 32% alpha at 3dp.

## Typography

**Display Font:** Fira Sans (with system sans-serif)
**Body Font:** Fira Sans
**Label/Mono Font:** Fira Sans with `tnum, lnum`. The build has no separate monospace face.

**Character:** Fira Sans is a humanist sans in the tradition of transit signage. With tabular lining figures on every style, fares, times and day numbers sit in columns the way they do on a departure board.

### Hierarchy
- **Headline** (600, 22sp, 28sp): the answer price. PriceBlock sets it Bold at this size when emphasised, and the diagram's terminus price is drawn Bold at 22sp. If the price would run off the edge, it scales down instead of overflowing.
- **Title Large** (600, 20sp, 26sp): top bar titles and empty-state titles.
- **Title** (600, 16sp, 22sp, 0.1sp): itinerary times, roundel codes (large), notice titles, section titles and fares in rows (Bold).
- **Title Small** (600, 14sp, 20sp, 0.1sp): sub-headings inside forms.
- **Body Large / Body** (400, 16/24sp and 14/20sp): form copy, notice details and detail rows.
- **Body Small** (400, 12sp, 16sp, 0.1sp): the busiest style. It carries itinerary metadata lines joined with " · " and source stamps.
- **Label / Label Small** (600 12sp 0.4sp; 500 11sp 0.5sp): roundel codes (Bold), day cells, the EUR echo and the SAMPLE DATA stamp (uppercase, Bold).

### Named Rules
**The Tabular Figures Rule.** Every text style carries `tnum, lnum`. Canvas-drawn prices set the feature explicitly, because a number that shifts width as it changes breaks the board.

**The CHF First Rule.** Prices read "CHF n" in the stronger style. EUR follows as "≈ €n" in Label Small, dim, and only when the setting is on.

## Layout

Layout is a single column, full-bleed and list-driven. Screen content uses a 16dp horizontal gutter. The rhythm runs on 4dp steps: 4dp between day cells, 8dp between controls, 12dp row padding and gap before the price, and 16dp gutters. Section titles open with 28dp of space above and 10dp below. Itinerary rows are separated by tone rather than by cards. Touch targets for row actions are at least 48dp tall.

The network diagram spans the full width. Its height is 64dp per origin or terminus row (the larger count, minimum 2) plus 24dp. Origin roundels are fixed-width 46dp plates so every line leaves from the same edge, and 108dp on the right is reserved for the terminus price. When parallel lines share a corridor, each is offset by 5dp like tracks.

At 600dp and wider, the bottom NavigationBar becomes a NavigationRail. Settings hides both. Drawing is edge-to-edge, with transparent system bars that follow the theme.

## Elevation & Depth

The system is flat. Depth comes only from tonal enamel steps. The ground is the base surface. Notices sit one step up (container), a selected row two steps up (container-high), and the live check result at the top step (container-highest). No custom shadows exist. Within the diagram, the underlay is set back with alpha (32% for lines, 50% for their rings) rather than with blur or lift.

### Named Rules
**The Enamel Step Rule.** Lift a surface by moving it one tonal container step. Shadows are never used to lift a surface.

## Shapes

There are two form families. Rectangular plates have small pressed corners: 3dp for stamps, 4dp for roundels, day cells and the live panel, and 6dp for service notices. Larger M3 surfaces such as sheets and dialogs take 8dp and 12dp from the theme. Circles are reserved for the network vocabulary: interchange circles (4dp underlay radius, 7dp traced), terminus rings (11dp radius, 3.5dp stroke) and the 36dp InterchangeRing on notices. Lines are 3dp round-capped in strips and the underlay, and 7dp with butt caps and round joins when traced. Station ticks are 2 to 2.5dp porcelain strokes.

Stock M3 buttons and segmented buttons keep their default fully rounded stadium ends. The build does not override them, so they are part of the shipped form.

### Named Rules
**The 45° Rule.** Route geometry runs horizontal, then bends to a pure 45° diagonal, then runs horizontal again. Do not use curves or arbitrary angles.

## Components

### Buttons
- **Shape:** M3 default fully rounded stadium.
- **Primary action in rows:** FilledTonalButton ("Check live price", with a Bolt icon), at least 48dp tall.
- **Secondary:** OutlinedButton ("Book", with a trailing open-in-new icon), at least 48dp tall. TextButton is used for low-weight actions in dialogs and settings.
- **States:** M3 defaults. The live-check button disables while a check is loading.

### Chips and Segmented Controls
- **Filter chips:** stock M3 FilterChip, used for multi-select filters on Plan.
- **Segmented buttons:** stock M3 single-choice rows for sort, trip type, stops, cabin and theme.

### Inputs / Fields
- **Style:** stock M3 OutlinedTextField, Slider, Switch, Checkbox and RadioButton, all themed by the enamel scheme.

### Navigation
- **Style:** M3 NavigationBar (or NavigationRail at 600dp and wider) with four outlined icons: Network (Hub), Departures (DepartureBoard), Plan (Tune) and Lines (Timeline). Each screen has a TopAppBar. On Network it shows the route title ("BSL · ZRH → Lisbon") in Title Large with a dates subtitle.

### Line Roundel (signature)
The line bullet from a station sign. It shows the IATA code in Bold on the line's ink, on a 4dp plate, with colours resolved by the Roundel Contrast Rule. Raw inks often miss 4.5:1 for both text colours (home scarlet #E2382B reaches only 3.85:1 on porcelain, so its roundel plate is darkened), which means the roundel fill is the resolved ink, not the raw ink. Small uses 2/6dp padding, Label Bold. Large uses 5/10dp, Title Bold. In the diagram it is a fixed-width 46dp plate.

### Itinerary Strip (signature)
One itinerary drawn as a strip of line. The row runs roundel, departure time, LineTrack, arrival time, destination code, then the price at the right edge. A metadata line below reads day · airline · duration · stops. The row is full-bleed with 12/16dp padding and steps up to container-high when selected. The detail section expands with a 220ms trace-eased height expand and a 160ms fade. LineTrack is a 3dp ink line with porcelain ticks at each end and one hollow porcelain circle per change. Rows outside the user's window turn their metadata amber.

### Network Diagram (signature)
Origins are stacked on the left as roundels and termini sit on the right as rings, joined by 45° lines. When the selection changes, the selected line re-traces from its origin over 520ms with TraceEasing, and each interchange circle appears as the trace reaches it. The selected terminus shows its code above a 22sp Bold price. Other termini show their code at 15sp and "from n" at 12sp, dim. While a scan runs, ScanningDiagram pulses each line toward a single unknown terminus. The pulse loops every 1400ms, staggered 0.12 per line, over an 18%-alpha track.

### Service Notice
A change-board notice. It is a 6dp container plate holding a 36dp InterchangeRing in the notice's ink with an outlined icon (calendar for cheaper date, trip origin for other origin, bolt for faster). The title and detail stack beside it and the Bold CHF price sits at the right. Tapping the notice traces the alternative on the network.

### Day Strip
A horizontal row of 56dp-wide cells on 4dp plates, 4dp apart. Each cell stacks weekday (Label Small), day number (Title) and price (Label). The cheapest day is inverted to the primary colour, in-window days use container-high and flex days stay on the ground. The selected day gets a 2dp amber border.

### Source Stamp
Every fare is attributed. Cached fares show a clock icon with "source · seen n ago · indicative, check live before booking" in Body Small, dim. Sample data shows an amber 3dp SAMPLE DATA plate followed by a plain sentence.

### Empty Line
An empty state drawn as an unfinished line: a 4dp outline-ink stroke at 70% width ending in a hollow 12dp terminus ring. Below it sit the title in Title Large, the body text and an optional action, all with 32dp padding.

## Do's and Don'ts

### Do:
- **Do** draw any route, journey or state with the network vocabulary: ink lines, porcelain ticks, hollow interchange circles and terminus rings.
- **Do** resolve every coloured plate that carries text through `roundelColors`, so it reaches 4.5:1 contrast.
- **Do** assign line inks by origin index, with scarlet always home, and reuse the same ink on every surface the origin appears on.
- **Do** use the trace easing (cubic-bezier 0.16, 1, 0.3, 1) for anything that draws or reveals along a line.
- **Do** show "CHF n" first, then "≈ €n" dim, and attach a SourceStamp to every fare.
- **Do** lift surfaces by tonal container step, from container to container-high to container-highest.

### Don't:
- **Don't** add drop shadows or elevation tints to lift surfaces.
- **Don't** bend route lines at any angle other than 45° or 90°, and don't curve them.
- **Don't** colour lines by anything but origin.
- **Don't** render a cached fare without its source and age, or without the indicative caveat.
- **Don't** let unselected lines compete with the traced line. They stay at 32% alpha and 3dp.
