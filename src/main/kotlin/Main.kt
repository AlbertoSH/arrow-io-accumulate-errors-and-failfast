import arrow.core.left
import arrow.data.*
import arrow.data.extensions.nonemptylist.semigroup.semigroup
import arrow.data.extensions.validated.applicative.applicative
import arrow.effects.IO
import arrow.effects.extensions.io.applicative.applicative
import arrow.effects.extensions.io.applicativeError.handleError
import arrow.effects.extensions.io.functor.map
import arrow.effects.extensions.io.fx.fx
import arrow.effects.fix


val v1: IO<Validated<Nel<String>, Int>> = IO {
  println("1")
  1.valid()
}

val v2: IO<Validated<Nel<String>, Int>> = IO {
  println("2")
  "some error computing 2".asInvalidNel()
}

val v3: IO<Validated<Nel<String>, Int>> = IO {
  println("3")
  3.valid()
}

val v4: IO<Validated<Nel<String>, Int>> = IO {
  println("4")
  "some error computing 4".asInvalidNel()
}


private fun <T> T.asInvalidNel() =
  /*
  Looks like there an issue with the compiler if using `.invalidNel()`
  Error:Kotlin: [Internal Error] java.lang.IllegalStateException: Backend Internal error: Exception during code generation
Cause: Back-end (JVM) Internal error: Cannot serialize error type: [ERROR : Unknown type parameter 0. Please try recompiling module containing "Class 'arrow.data.ValidatedKt'"]
Cause: Cannot serialize error type: [ERROR : Unknown type parameter 0. Please try recompiling module containing "Class 'arrow.data.ValidatedKt'"]
File being compiled at position: (65,18) in [...]/arrow-io-accumulate-errors-and-failfast/src/main/kotlin/Main.kt
The root cause was thrown at: SerializerExtension.kt:87
File being compiled at position: file:///[...]/arrow-io-accumulate-errors-and-failfast/src/main/kotlin/Main.kt
The root cause was thrown at: ExpressionCodegen.java:322
	at org.jetbrains.kotlin.codegen.CompilationErrorHandler.lambda$static$0(CompilationErrorHandler.java:24)
	...
   */
  ValidatedNel.fromEither(this.nel().left())


val asList: Nel<IO<Validated<Nel<String>, Int>>> = Nel.of(v1, v2, v3, v4)

fun accumulateInvalids(): IO<Validated<Nel<String>, Nel<Int>>> =
  asList.reversed()
    .sequence(IO.applicative())
    .map { it.sequence(ValidatedNel.applicative(Nel.semigroup<String>())).fix() }
    .map {
      it.reversed()
    }

private fun <E, A> Validated<Nel<E>, Nel<A>>.reversed() = bimap(
  { it.reversed() },
  { it.reversed() }
)


fun failFast(): IO<Validated<Nel<String>, Nel<Int>>> =
// The order of execution should be v1 -> v2 -> v3 -> v4
// Since the execution is done in a reversed way (it executes first the latest value of the list)
  // we reverse it
  asList.reversed()
    .map {
      it.flatMap {
        it.fold(
          {
            IO.raiseError<Int>(RuntimeException(it.all.joinToString("\n")))
          },
          { IO.just(it) }
        )
      }
    }
    .sequence(IO.applicative()).fix()
    .map { it.valid() }
    .handleError { it.message!!.asInvalidNel() }


private fun <A> NonEmptyList<A>.reversed(): NonEmptyList<A> {
  return Nel.fromListUnsafe(this.all.reversed())
}


fun main() = fx {
  println(
    """
    accumulateInvalids() should execute all effects in the order they are defined and accumulate errors
    It should print
    1
    2
    3
    4
    Invalid(e=NonEmptyList(all=[some error computing 2, some error computing 4]))
  """.trimIndent()
  )
  println("The real result is")
  println(!accumulateInvalids())

  println("\n\n\n")

  println(
    """
    failFast() should execute effects until an `Invalid` result is reached
    In this scenario, second scenario is the one that will return an `Invalid`
    It should print
    1
    2
    Invalid(e=NonEmptyList(all=[some error computing 2]))
  """.trimIndent()
  )
  println("The real result is")
  println(!failFast())
}.unsafeRunSync()

