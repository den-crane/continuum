package continuum

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}

import org.scalatest.propspec.AnyPropSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import continuum.test.Generators

class SerializationSpec
    extends AnyPropSpec
    with ScalaCheckPropertyChecks
    with Matchers
    with Generators {

  def roundTrip[A](a: A): A = {
    val bytes = new ByteArrayOutputStream()
    val out = new ObjectOutputStream(bytes)
    out.writeObject(a)
    out.close()
    // Resolve classes against the test classloader: the default lookup cannot see project
    // classes under sbt's layered test classloaders.
    val loader = getClass.getClassLoader
    val in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray)) {
      override def resolveClass(desc: java.io.ObjectStreamClass): Class[_] =
        Class.forName(desc.getName, false, loader)
    }
    in.readObject().asInstanceOf[A]
  }

  property("cuts serialize round-trip") {
    forAll { (cut: Cut[Int]) =>
      roundTrip(cut) should equal(cut)
    }
  }

  property("intervals serialize round-trip and remain usable") {
    forAll { (interval: Interval[Int], point: Int) =>
      val revived = roundTrip(interval)
      revived should equal(interval)
      revived(point) should be(interval(point))
    }
  }

  property("interval sets serialize round-trip and remain usable") {
    forAll { (set: IntervalSet[Int], interval: Interval[Int]) =>
      val revived = roundTrip(set)
      revived should equal(set)
      revived.encloses(interval) should be(set.encloses(interval))
      (revived + interval) should equal(set + interval)
    }
  }
}
