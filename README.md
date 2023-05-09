# class-path-inspector

A tiny library to help inspect a JVM app class path.

From Scala CLI:
```scala
//> using lib "io.github.alexarchambault::class-path-inspector:latest.release"
classpath.Inspector.classPath().toVector // Vector[(ClassLoader, Seq[URL])]
```

From Ammonite:
```scala
import $ivy.`io.github.alexarchambault::class-path-inspector:latest.release`
classpath.Inspector.classPath().toVector // Vector[(ClassLoader, Seq[URL])]
```

`Inspector.classPath` returns loaders and their URLs from the closest loader
(`Thread.currentThread().getContextLoader` by default, unless a different
loader is passed as argument), up to the JVM app class loader and ext / platform
class loader.
