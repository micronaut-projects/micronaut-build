# Docs Template Design Analysis

This document covers the current docs template bundle in:

`micronaut-gradle-plugins/src/main/template`

It also accounts for the generated fixture output at:

`test-suite/projects/docs-layout/build/docs`

## Current Design

The guide now uses a static, shadcn-inspired shell without Node.js:

- `style/layout.html` renders the main guide.
- `style/page.html` renders the configuration reference.
- `css/custom.css` owns the shell tokens, layout, typography, tables,
  admonitions, code colors, dark mode, print rules, and responsive behavior.
- `css/multi-language-sample.css` owns the snippet selector UI.
- `js/guide.js` owns theme switching, sidebar collapse/drawer behavior, TOC
  highlighting, and local copy-to-clipboard behavior.
- `js/multi-language-sample.js` groups adjacent language samples, builds local
  selectors, and avoids merging a repeated language into the previous group.
- `js/highlight.pack.js` remains the local Highlight.js runtime.
- `img/micronaut-logo-white.svg` is the only bundled image asset.

The primary generated guide output loads only:

- `css/custom.css`
- `css/multi-language-sample.css`
- `js/highlight.pack.js`
- `js/guide.js`
- `js/multi-language-sample.js`
- `js/docs.js`
- `img/micronaut-logo-white.svg`

The configuration reference output loads the same shell stylesheet and local JS,
without the multi-language sample stylesheet because that page does not use it.

## What Was Removed

The old browser-facing template bundle has been reduced aggressively. These
unused assets were removed from `src/main/template`:

- legacy CSS: `main.css`, `skin.css`, `tools.css`, `ref.css`, `menu.css`,
  `pdf.css`, and `custom-pdf.css`
- the `css/highlight/` directory and all Highlight.js theme files
- Font Awesome font files under `fonts/`
- old GIF/PNG UI assets under `img/default/`
- old `note.gif` and `warning.gif`
- dormant `style/index.html` and `style/menu.html`
- hard-coded analytics from legacy section/reference templates

The one Highlight.js theme that was still referenced, Agate, was folded into
`custom.css`. Admonitions no longer rely on Font Awesome glyphs; labels are CSS
text badges.

`DocPublisher` now copies only current CSS assets and no longer copies the
`style/` source templates into generated docs output. The generated docs output
therefore no longer ships source templates as browser assets.

`DocPublisher` also no longer creates an empty generated `ref/` directory when a
project has no reference docs.

The functional-test resource jar,
`micronaut-gradle-plugins/src/functionalTest/resources/grails-doc-files.jar`,
has been regenerated from the slim template bundle so tests use the same assets
as the source template directory.

## Generated Output

The generated guide now has:

- an HTML5 document
- a left sidebar with nested TOC entries
- a sticky topbar with breadcrumb, API/configuration links, sidebar controls,
  and theme toggle
- a constrained content column
- local copy buttons without Clipboard.js
- token-based light and dark themes
- responsive mobile drawer behavior
- shadcn-like borders, muted backgrounds, buttons, table styling, and code
  blocks

The generated configuration reference now uses the same shell and design system
as the guide.

The generated docs still include Javadoc output under `api/`. Those assets are
owned by the JDK Javadoc tool, not by this guide template bundle.

## Remaining Template Files

The active template bundle is now:

- `css/custom.css`
- `css/multi-language-sample.css`
- `img/micronaut-logo-white.svg`
- `js/docs.js`
- `js/guide.js`
- `js/highlight.pack.js`
- `js/multi-language-sample.js`
- `style/guideItem.html`
- `style/layout.html`
- `style/page.html`
- `style/referenceItem.html`
- `style/section.html`
- `log4j.properties`

`guideItem.html` and `referenceItem.html` are retained because `DocPublisher`
still reads them while rendering individual guide/reference pages. They are not
copied into generated docs output anymore.

## Remaining Improvement Opportunities

- Replace inline event handlers in templates with event listeners in `guide.js`
  and `multi-language-sample.js`.
- Replace deprecated Highlight.js calls with `hljs.highlightAll()`.
- Decide whether `js/docs.js` can be merged into `guide.js` or removed after
  confirming no legacy generated pages call its helpers.
- Consider moving the root generated redirect from the old HTML4 literal in
  `DocPublisher.groovy` to the same HTML5 style as the rest of the shell.
- Add a skip link for keyboard users.
- Emit explicit snippet group wrappers from the renderer instead of relying on
  adjacent sibling grouping.
- Convert multi-language selectors into full ARIA tabs or keep real buttons
  with clearer `aria-pressed` state.
- Review `style/guideItem.html` and `style/referenceItem.html` for whether
  downstream consumers still need individual page/reference output. If not,
  remove those templates and simplify `DocPublisher`.

## Verification Targets

The docs layout fixture verifies that generated output contains the new shell,
the sidebar/topbar controls, local scripts, generated TOC entries, macro output,
multi-language snippets, API links, and configuration reference content.

It also guards against reintroducing old linked assets:

- `../css/main.css`
- `../css/pdf.css`
- external Clipboard.js
