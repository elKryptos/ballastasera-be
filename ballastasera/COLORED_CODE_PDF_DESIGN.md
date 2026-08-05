# Colored Code PDF Design

## Context

`ballastasera/GUIA_SPRING_BOOT_Y_ARQUITECTURA.pdf` is a personal Spring Boot guide generated from a temporary HTML document. Its code samples currently use a single light color, which makes Java, SQL, configuration, and terminal examples harder to scan.

## Goal

Regenerate the PDF with syntax-colored code blocks while preserving the existing dark Darcula-style presentation, page layout, database diagram, and Spanish content.

## Approach

Rebuild the HTML source with local HTML/CSS token coloring instead of adding a runtime library. This keeps PDF generation deterministic and works offline with the existing Chrome headless workflow.

## Supported Code Types

- Java: annotations, keywords, types/classes, methods/fields, strings, numbers, and comments.
- SQL: keywords, identifiers, strings, numbers, and comments.
- YAML/properties: keys, values, strings, numbers/booleans, and comments.
- Maven/XML: tags, attributes, values, and comments.
- Terminal/Maven commands: commands, flags, arguments, strings, and comments.

## Visual Rules

- Keep a dark code background and monospaced font.
- Use high-contrast Darcula-like colors.
- Use consistent token colors across languages where the semantic role is shared.
- Preserve wrapping and readable line spacing when printed to PDF.

## Validation

- Generate the PDF with Chrome headless.
- Confirm the PDF exists and has a non-trivial file size.
- Confirm the temporary HTML source is removed afterward.
- Confirm the PDF remains ignored by Git.
- Read the generated PDF to verify its pages and colored code content are present.
