package com.example

import io.github.boazy.kbuilder.annotations.GenerateBuilder


typealias Foo<T, R> = TestDataClass2<T, R>
typealias Bar = TestDataClass2<String, Int>

class IntArrayList : ArrayList<Int>()

@GenerateBuilder
data class TestDataClass1(val counter: Int = 1, val name: List<String?>) {

  constructor() : this(0, listOf())

  fun copy(): TestDataClass1 = TestDataClass1()
  fun copy(name: List<String?>): TestDataClass1 = TestDataClass1(counter = this.counter, name = name)
}

@GenerateBuilder
@Suppress("with")
data class TestDataClass2<out T, R>(val generic1: T, val generic2: R)

@GenerateBuilder
data class TestDataClass3<out T : Any, R>(val foo: Foo<T, R>, val bar: Bar?)

@GenerateBuilder
data class TestDataClass4<Z, out T : TestDataClass2<Z, Z>, R>(val a: TestDataClass2<T, R>, val b: TestDataClass2<Z, *>)

@GenerateBuilder(prefix = "set")
data class TestDataClass5<out T, R>(val foo: Foo<T, R>, val list: IntArrayList?, val mList: MutableList<Any?>?)

@GenerateBuilder
data class TestDataClass6(val a: Int, val b: String = "foo")

class Parent {
  @GenerateBuilder
  data class TestDataClass6<out T, R>(val foo: Foo<T, R>, val list: IntArrayList?) where R : Any, R : Runnable
}

@GenerateBuilder
data class Test(val foo: Int = 1, val bar: String)

@GenerateBuilder(className = "OtherNameBuilder")
data class TestName(val foo: Int)

@GenerateBuilder(optimizeCopy = true)
data class TestOptimizeCopy(val a: Int = 1, val b: Double = 2.0, val c: String = "x")