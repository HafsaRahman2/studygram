# StudyGram — frontend

React 19 + TypeScript, built with Vite.

See the [project README](../README.md) for the architecture, setup instructions and API reference.

## Commands

```bash
npm install     # install dependencies
npm run dev     # dev server on http://localhost:5173
npm run build   # type-check and build for production
npm run lint    # oxlint
```

## Configuration

The backend URL defaults to `http://localhost:8080`. To point at a deployed API, set `VITE_API_URL`
before building:

```bash
VITE_API_URL=https://api.example.com npm run build
```

Vite only exposes variables prefixed `VITE_` to browser code — anything else stays server-side.

## Structure

| Path | Purpose |
| --- | --- |
| `src/api.ts` | Every backend call, typed. The base URL lives here and nowhere else. |
| `src/types.ts` | Interfaces mirroring the backend DTOs. |
| `src/App.tsx` | Routing and auth state only. |
| `src/hooks/` | `useAuth` (session), `useBreak` (break timer), `useTopics` (topic list). |
| `src/components/` | One file per screen, plus shared pieces in `ui.tsx`. |
| `src/utils/format.ts` | Relative timestamps, avatar initials and colours. |
