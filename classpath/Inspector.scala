//> using scala "2.12", "2.13", "3"
//> using jvm "8"
//> using lib "com.lihaoyi::os-lib:0.9.1"
//> using lib "org.scala-lang.modules::scala-collection-compat:2.9.0"
//> using option "-deprecation"

package classpath

import java.io.File
import java.net.URL
import java.net.URLClassLoader

import scala.collection.compat._
import immutable.LazyList

object Inspector {

  private lazy val builtinClazzLoader =
    try Class.forName("jdk.internal.loader.BuiltinClassLoader")
    catch {
      case _: ClassNotFoundException => null
    }

  lazy val isBuiltinClassLoader: ClassLoader => Boolean =
    if (builtinClazzLoader == null)
      _ => false
    else
      cl => builtinClazzLoader.isInstance(cl)

  lazy val tryGetUrls: ClassLoader => Option[Array[URL]] = {

    // To work fine, this requires
    //   --add-opens java.base/jdk.internal.loader=ALL-UNNAMED

    // originally based on https://stackoverflow.com/questions/49557431/how-to-safely-access-the-urls-of-all-resource-files-in-the-classpath-in-java-9

    val clazz =
      try Class.forName("jdk.internal.loader.URLClassPath")
      catch {
        case _: ClassNotFoundException => null
      }

    val noop = (cl: ClassLoader) => None

    if (builtinClazzLoader != null && clazz != null) {
      val ucpField =
        try {
          val f = builtinClazzLoader.getDeclaredField("ucp")
          f.setAccessible(true)
          f
        }
        catch {
          case _: NoSuchFieldException =>
            null
          case e: Exception if e.getClass.getName == "java.lang.reflect.InaccessibleObjectException" =>
            null
        }
      val getURLs =
        try clazz.getMethod("getURLs")
        catch {
          case _: NoSuchMethodException =>
            null
        }

      if (ucpField != null && getURLs != null) {
        (cl: ClassLoader) =>
          val ucpObject = ucpField.get(cl)
          if (ucpObject == null) None
          else {
            println("using builtin")
            Some(getURLs.invoke(ucpObject).asInstanceOf[Array[URL]])
          }
      }
      else
        noop
    }
    else
      noop
  }

  def classPath(loader: ClassLoader = Thread.currentThread().getContextClassLoader): LazyList[(ClassLoader, Seq[URL])] =
    if (loader == null) LazyList.empty
    else {
      val urls = loader match {
        case ucl: URLClassLoader =>
          ucl.getURLs().toSeq
        case cl =>
          val viaBuiltinOpt =
            if (isBuiltinClassLoader(cl))
              tryGetUrls(cl).map(_.toSeq)
            else
              None
          viaBuiltinOpt
            .orElse {
              if (cl.getClass.getName == "jdk.internal.loader.ClassLoaders$AppClassLoader")
                Some {
                  println("using java.class.path")
                  expandClassPath(sys.props.getOrElse("java.class.path", ""))
                    .map(_.toNIO.toUri.toURL)
                }
              else
                None
            }
            .getOrElse(Nil)
      }

      (loader, urls) #:: classPath(loader.getParent)
    }

  def expandClassPathEntry(entry: String): Seq[os.Path] =
    if (entry.endsWith("/*")) {
      val path = os.Path(entry.stripSuffix("/*"), os.pwd)
      if (os.isDir(path)) os.list(path)
      else Nil
    }
    else if (entry.endsWith("/*.jar")) {
      val path = os.Path(entry.stripSuffix("/*"), os.pwd)
      if (os.isDir(path)) os.list(path).filter(_.last.endsWith(".jar"))
      else Nil
    }
    else {
      val path = os.Path(entry, os.pwd)
      if (os.exists(path)) Seq(path)
      else Nil
    }

  def expandClassPath(input: String): Seq[os.Path] =
    input
      .split(File.pathSeparator)
      .toSeq
      .filter(_.nonEmpty)
      .flatMap(expandClassPathEntry)

}
