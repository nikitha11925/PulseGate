/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        // Dark terminal palette (see CLAUDE.md design rules)
        bg: '#0D0D0D',
        surface: '#1A1A1A',
        edge: '#2A2A2A',
        primary: '#E8E8E8',
        muted: '#666666',
        accent: '#00FF88', // healthy / DONE
        warn: '#FFB800',   // FAILED / retrying
        danger: '#FF4444', // DEAD
      },
      fontFamily: {
        mono: ['"JetBrains Mono"', '"Fira Code"', 'monospace'],
        sans: ['Inter', 'system-ui', 'sans-serif'],
      },
      borderRadius: {
        // Sharp edges — never more than 4px.
        DEFAULT: '4px',
        none: '0',
      },
    },
  },
  plugins: [],
}
