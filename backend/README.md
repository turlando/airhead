# AirHead Backend

## Dependencies

- Java >= 8
- [Leiningen](https://leiningen.org/)
- FFmpeg (runtime dependency)
- libshout (runtime dependency)
- Icecast 2 (running in some other server too)

## Configuration

The configuration is almost self-explanatory. Make sure you have a
valid configuration in either `/usr/local/etc/airhead/airhead.edn`,
`/etc/airhead/airhead.edn`, or `./airhead.edn` (current directory)
in such order before starting the backend.

## Hacking

### Getting a REPL

Just run `lein repl`. The first time you invoke this command it will download
the required runtime dependencies.

```
% cd into-this-directory
% lein repl
[...]
airhead.core=>
```

### Starting and stopping the backend

Just call the `start!` function.

```
airhead.core=> (def s (start!))
```

It will also bind the application state to the variable `s`. This will allow us
to stop the backend with the `stop!` function.

```
airhead.core=> (stop! s)
```

### Development with Emacs

Install CIDER and then do `M-x cider-jack-in`.
