// The app's one way online (ROADMAP F11.3): an HTTP GET for http and https only, with a size cap
// and short timeouts. Only a module that depends on this one can reach the network at all.
plugins {
    id("actiondraw.kotlin-library")
}
