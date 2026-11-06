#!/usr/bin/env python3
"""Fix CSS custom properties without fallback values by adding Bootstrap 5 default fallbacks."""
import re
import os

WORKDIR = "/modernize-data/studio-data/TNT1001/APP629179/transformed-code/227/studio-workspace/Rollerbasic2"

# Bootstrap 5 default values for CSS custom properties
BS_DEFAULTS = {
    '--bs-body-bg': '#ffffff',
    '--bs-body-color': '#212529',
    '--bs-border-color': '#dee2e6',
    '--bs-border-radius': '0.25rem',
    '--bs-border-radius-lg': '0.375rem',
    '--bs-border-radius-pill': '50rem',
    '--bs-border-radius-sm': '0.2rem',
    '--bs-tertiary-bg': '#f8f9fa',
    '--bs-primary-bg-subtle': '#cfe2ff',
    '--bs-primary-text-emphasis': '#0a58ca',
    '--bs-secondary-color': '#6c757d',
    '--bs-secondary-bg': '#e9ecef',
    '--bs-primary': '#0d6efd',
    '--bs-danger': '#dc3545',
    '--bs-danger-text-emphasis': '#dc3545',
    '--bs-link-color': '#0d6efd',
    '--bs-success-text-emphasis': '#198754',
    '--bs-focus-ring-x': '0',
    '--bs-focus-ring-y': '0',
}

# Theme variables defaults (for terminal theme)
THEME_DEFAULTS = {
    '--accent': '#007acc',
    '--background': '#ffffff',
    '--code-border': '#dee2e6',
    '--comment': '#6a9955',
    '--font-size': '14px',
    '--foreground': '#24292e',
    '--line-height': '1.5',
    '--radius': '4px',
}

def add_css_fallbacks(content, defaults):
    """Add fallback values to var() calls that don't have them."""
    def replace_var(match):
        var_name = match.group(1)
        full_match = match.group(0)
        fallback = defaults.get(var_name)
        if fallback:
            return f'var({var_name}, {fallback})'
        return full_match

    # Match var(--something) without a comma (no fallback)
    pattern = r'var\((--[a-zA-Z0-9_-]+)\)'
    return re.sub(pattern, replace_var, content)

# Fix roller.css
roller_css = os.path.join(WORKDIR, "app/src/main/webapp/roller-ui/styles/roller.css")
if os.path.exists(roller_css):
    with open(roller_css, 'r') as f:
        content = f.read()
    content = add_css_fallbacks(content, BS_DEFAULTS)
    with open(roller_css, 'w') as f:
        f.write(content)
    print(f"Fixed: {roller_css}")

# Fix terminal/theme.css
theme_css = os.path.join(WORKDIR, "app/src/main/webapp/themes/terminal/theme.css")
if os.path.exists(theme_css):
    with open(theme_css, 'r') as f:
        content = f.read()
    content = add_css_fallbacks(content, THEME_DEFAULTS)
    with open(theme_css, 'w') as f:
        f.write(content)
    print(f"Fixed: {theme_css}")

print("CSS fixes complete!")
