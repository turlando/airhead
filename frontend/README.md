# Airhead Frontend

## Dependencies

- Java >= 8
- [Leiningen](https://leiningen.org/)

## Hacking

### Getting a Figwheel REPL

```
lein dev
```

### Start Figwheel from CIDER

Navigate to a clojurescript file and start a figwheel REPL with
`cider-jack-in-clojurescript` or (`C-c M-J`).

### Chrome cljs-devtools

To enable:

1. Open Chrome's DevTools,`Ctrl-Shift-i`
2. Open "Settings", `F1`
3. Check "Enable custom formatters" under the "Console" section
4. close and re-open DevTools

## Build

```
lein build
```
