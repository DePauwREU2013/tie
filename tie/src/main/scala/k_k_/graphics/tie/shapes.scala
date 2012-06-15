/*
   file: k_k_/graphics/tie/shapes.scala

   Tie - Tie Illustrates Everything.

     http://www.tie-illustrates-everything.com/

   Copyright (c)2010-2012 by Corbin "Kip" Kohn
   All Rights Reserved.

   Please reference the following for applicable terms, conditions,
   and licensing:

     http://tie-illustrates-everything.com/licensing
*/

package k_k_.graphics.tie

package shapes {

import java.io.{File, FileNotFoundException, ByteArrayInputStream}
import javax.imageio.ImageIO

import k_k_.graphics.tie.shapes.path._
import k_k_.graphics.tie.shapes.text.{Text, Default_Text_Ruler_Factory}
import k_k_.graphics.tie.effects._
import k_k_.graphics.tie.ink.Pen
import k_k_.graphics.tie.transform._

import k_k_.algo.{Math => More_Math}


object Rectangular {

  def unapply(rect: Rectangular): Option[(Double, Double)] =
    Some(rect.width, rect.height)
}

/**
 * Mixin abstraction for specifying that a type has rectangular dimensions
 * (i.e. `width` x `height`).
 */
trait Rectangular {

  val width: Double
  val height: Double
}


object Bounding_Boxed {

  def apply(bboxed: Bounding_Boxed): Ortho_Rectangle =
    bboxed match {
      case ortho_rect: Ortho_Rectangle => ortho_rect
      case _                           => bboxed.bounding_box
    }
}

trait Bounding_Boxed {

  def bounding_box: Ortho_Rectangle

  def center_pt: Point =
    bounding_box.center_pt

  final def bounding_box_shape: Pre_Formulated_Shape =
    bounding_box.as_drawing_shape
}

trait Bounding_Shaped { self: Bounding_Boxed =>

  def best_bounding_shape: Pre_Formulated_Shape =
    bounding_box.as_drawing_shape
}


sealed trait Shape_Op { self: Drawing_Shape =>
}


object Nullary_Shape_Op {

  // WARNING: be careful when matching to not forget the empty parens ('()'),
  // so the compiler will know not to match this object (when matching an
  // extension of its companion trait is what is desired).  i.e. use:
  //   case nop @ Nullary_Shape_Op() => ...
  // not:
  //   case nop @ Nullary_Shape_Op => ...
  // or prepare for the error: pattern type is incompatible with expected type;
  // [INFO]  found   : object k_k_.graphics.tie.shapes.Nullary_Shape_Op
  // [INFO]  required: k_k_.graphics.tie.shapes.Drawing_Shape
  // [INFO]         case Nullary_Shape_Op => (None, true)
  // [INFO]              ^
  def unapply(s: Nullary_Shape_Op): Boolean =
    true
}

trait Nullary_Shape_Op extends Shape_Op { self: Drawing_Shape =>
}


object Unary_Shape_Op {

  def unapply(s: Unary_Shape_Op): Option[Drawing_Shape] =
    Some(s.shape)
}

trait Unary_Shape_Op extends Shape_Op { self: Drawing_Shape =>

  val shape: Drawing_Shape

  def child =
    shape

  def child_=(replacement_shape: Drawing_Shape) =
    replace(replacement_shape)

  // NOTE: `with` included in result type for symmetry with Binary_Shape_Op;
  // does enable the ridiculous: (u_shape_op.child = shape1).child = shape2
  def replace(replacement_shape: Drawing_Shape):
    Drawing_Shape with Unary_Shape_Op
}


object Binary_Shape_Op {

  def unapply(s: Binary_Shape_Op): Option[(Drawing_Shape, Drawing_Shape)] =
    Some(s.left, s.right)
}

trait Binary_Shape_Op extends Shape_Op { self: Drawing_Shape =>

  def left:  Drawing_Shape

  def right: Drawing_Shape

  def left_=(replacement_shape: Drawing_Shape) =
    replace_left(replacement_shape)

  def right_=(replacement_shape: Drawing_Shape) =
    replace_right(replacement_shape)

  // NOTE: `with` in result type allows for chaining in the form of:
  //   (bin_shape_op.left = shape1).right = shape2
  def replace(replacement_left: Drawing_Shape = left,
              replacement_right: Drawing_Shape = right):
    Drawing_Shape with Binary_Shape_Op

  def replace_left(replacement_left: Drawing_Shape) =
    replace(replacement_left, right)

  def replace_right(replacement_right: Drawing_Shape) =
    replace(left, replacement_right)
}


trait Drawing_Shape_Traversal { self: Drawing_Shape =>

  final def mapped(f: Drawing_Shape => Drawing_Shape): Drawing_Shape =
    this match {
      case nshape @ Nullary_Shape_Op()    => f(nshape)
      case ushape @ Unary_Shape_Op(s)     => ushape.replace(f(s))
      case bshape @ Binary_Shape_Op(l, r) => bshape.replace(f(l), f(r))
    }

  // NOTE: distinct name precludes 'missing param type for expanded func' error
  final def mapped_when(pf: PartialFunction[Drawing_Shape, Drawing_Shape]):
      Drawing_Shape = {

    val lifted_pf = pf.lift

    def walk(curr_shape: Drawing_Shape): Option[Drawing_Shape] =
      curr_shape match {
        case nshape @ Nullary_Shape_Op()    => lifted_pf(nshape)
        case ushape @ Unary_Shape_Op(s)     => walk(s).map(ushape.replace(_))
        case bshape @ Binary_Shape_Op(l, r) =>
          walk(l) match {
            case Some(s) => walk(r).map(bshape.replace(s, _)).orElse(
                                   Some(bshape.replace(s, r)))
            case None    => walk(r).map(bshape.replace(l, _))
          }
      }
    walk(this).getOrElse(this)

// impl. which would always rebuild the entire tree
//    def walk(curr_shape: Drawing_Shape): Drawing_Shape =
//      curr_shape match {
//        case s if pf.isDefinedAt(s)         => pf(s)
//        case nshape @ Nullary_Shape_Op()    => nshape
//        case ushape @ Unary_Shape_Op(s)     => ushape.replace(walk(s))
//        case bshape @ Binary_Shape_Op(l, r) => bshape.replace(walk(l),walk(r))
//      }
//    walk(this)//way!
  }

  final def map[T](ordering: => Seq[Drawing_Shape])
                  (f: Drawing_Shape => T): Seq[T] =
    ordering map f

  // pre-order traversal:
  final def map[T](f: Drawing_Shape => T): Seq[T] =
    map(pre_order)(f)

  final def collect[T](ordering: => Seq[Drawing_Shape])
                      (pf: PartialFunction[Drawing_Shape, T]): Seq[T] =
    (ordering map pf.lift) filter ( _ ne None ) map ( _.get )

  // pre-order traversal:
  final def collect[T](pf: PartialFunction[Drawing_Shape, T]): Seq[T] =
    collect(pre_order)(pf)

  final def contains(ordering: => Seq[Drawing_Shape])
                    (elem: Drawing_Shape): Boolean =
    ordering contains elem

  final def contains(elem: Drawing_Shape): Boolean =
    contains(pre_order)(elem)


  final def count(ordering: => Seq[Drawing_Shape])
                 (pred: Drawing_Shape => Boolean): Int =
    ordering count pred

  final def count(pred: Drawing_Shape => Boolean): Int =
    count(pre_order)(pred)


  final def exists(ordering: => Seq[Drawing_Shape])
                  (pred: Drawing_Shape => Boolean): Boolean =
    ordering exists pred

  final def exists(pred: Drawing_Shape => Boolean): Boolean =
    exists(pre_order)(pred)


  final def find(ordering: => Seq[Drawing_Shape])
                (pred: Drawing_Shape => Boolean): Option[Drawing_Shape] =
    ordering find pred

  final def find(pred: Drawing_Shape => Boolean): Option[Drawing_Shape] =
    find(pre_order)(pred)


  final def pre_order: Seq[Drawing_Shape] = {
    def walk_in_steps(shape: Drawing_Shape): Stream[Drawing_Shape] =
      shape match {
        case nshape @ Nullary_Shape_Op() =>
          Stream(nshape)
        case ushape @ Unary_Shape_Op(s) =>
          Stream.cons(ushape, walk_in_steps(s))
        case bshape @ Binary_Shape_Op(l, r) =>
          Stream.cons(bshape, walk_in_steps(l)) append walk_in_steps(r)
      }
    walk_in_steps(this)
  }

  final def post_order: Seq[Drawing_Shape] = {
    def walk_in_steps(shape: Drawing_Shape): Stream[Drawing_Shape] =
      shape match {
        case nshape @ Nullary_Shape_Op() =>
          Stream(nshape)
        case ushape @ Unary_Shape_Op(s) =>
          walk_in_steps(s) append Stream(ushape)
        case bshape @ Binary_Shape_Op(l, r) =>
          walk_in_steps(l) append walk_in_steps(r) append Stream(bshape)
      }
    walk_in_steps(this)
  }
}


sealed abstract class Clip_Rule
case object Non_Zero_Clip extends Clip_Rule // default
case object Even_Odd_Clip extends Clip_Rule
case object Inherit_Clip  extends Clip_Rule


trait Presentable_Shape[T <: Presentable_Shape[T]] { self: T =>

  def combo(over: T): T =
    create_composite_shape(over)

  def -&(over: T): T =
    combo(over)


  def clip_by(clipping: T, rule: Clip_Rule): T =
    create_clipped_shape(clipping, rule)

  def clip_by(clipping: T): T =
    create_clipped_shape(clipping, Non_Zero_Clip)

  def -<>(clipping: T, rule: Clip_Rule): T =
    clip_by(clipping, rule)

  def -<>(clipping: T): T =
    clip_by(clipping)


  def mask_by(mask: T): T =
    create_masked_shape(mask)

  def -<#>(mask: T): T =
    mask_by(mask)


  def using(pen: Pen): T =
    create_inked_shape(pen)

  def -~(pen: Pen): T =
    using(pen)


  def exhibit(effect: Effect): T =
    create_effected_shape(effect)

  def exhibit(opacity: Double): T =
    exhibit(Opacity_Effect(opacity))

  def exhibit(filter: Filter): T =
    exhibit(Filter_Effect(filter))


  def -#(effect: Effect): T =
    exhibit(effect)

  def -#(opacity: Double): T =
    exhibit(opacity)

  def -#(filter: Filter): T =
    exhibit(filter)


  def as(attribution: Attribution): T =
    create_attributed_shape(attribution)

  def as(attribution1: Attribution, attribution2: Attribution): T =
    as(attribution1).as(attribution2)

  def as(id: String): T =
    create_attributed_shape(Id_Attribution(id))


  def -:(attribution: Attribution): T =
    as(attribution)

  def -:(attribution1: Attribution, attribution2: Attribution): T =
    as(attribution2).as(attribution1) // parallel right-assoc. evaluation

  def -:(id: String): T =
    as(Id_Attribution(id))


  def -:-(attribution: Attribution): T =
    as(attribution)

  def -:-(attribution1: Attribution, attribution2: Attribution): T =
    as(attribution1).as(attribution2)

  def -:-(id: String): T =
    as(Id_Attribution(id))


  protected def create_composite_shape(other: T): T

  protected def create_clipped_shape(clipping: T, rule: Clip_Rule): T

  protected def create_masked_shape(mask: T): T

  protected def create_inked_shape(pen: Pen): T

  protected def create_effected_shape(effect: Effect): T

  protected def create_attributed_shape(attribution: Attribution): T
}


object Drawing_Shape {

  /** calculate 'least common fit' bounding box, with center at (0, 0)
   *
   *  definition: the 'least common fit' bounding box is the smallest bounding
   *  box capable of fully containing every respective bounding box of all
   *  `shapes`, if each of their bounding box were centered at (0, 0)
   */
  def common_fit_bounding_box(shapes: Traversable[Drawing_Shape]):
      Ortho_Rectangle =
    Origin_Ortho_Rectangle.apply _ tupled
      ((0.0, 0.0) /: shapes) { (cf_dims, shape) =>
        val bb = shape.bounding_box
        (cf_dims._1 max bb.width,
         cf_dims._2 max bb.height)
      }
}

sealed abstract class Drawing_Shape
    extends Transforming[Drawing_Shape]
       with Placeable[Drawing_Shape]
       with Presentable_Shape[Drawing_Shape]
       with Bounding_Boxed with Bounding_Shaped
       with Drawing_Shape_Traversal { self: Shape_Op =>

  // technically, this method with it's 'conversion' is useless; yet, it nicely
  // captures a useful invariant
  final def as_shape_op: Shape_Op =
    this


  type Translated_T          = Translated_Non_Pre_Formulated_Shape
  protected val Translated   = Translated_Non_Pre_Formulated_Shape

  type Scaled_T              = Scaled_Non_Pre_Formulated_Shape
  protected val Scaled       = Scaled_Non_Pre_Formulated_Shape

  type Rotated_T             = Rotated_Non_Pre_Formulated_Shape
  protected val Rotated      = Rotated_Non_Pre_Formulated_Shape

  type Reflected_T           = Reflected_Non_Pre_Formulated_Shape
  protected val Reflected    = Reflected_Non_Pre_Formulated_Shape

  type Skewed_Horiz_T        = Skewed_Horiz_Shape
  protected val Skewed_Horiz = Skewed_Horiz_Shape

  type Skewed_Vert_T         = Skewed_Vert_Shape
  protected val Skewed_Vert  = Skewed_Vert_Shape


  protected def create_composite_shape(other: Drawing_Shape): Drawing_Shape = {

    // combo successive Invis_Rectangle`s (for padding) into singular containing

    // NOTE: equiv. algo not possible to implement as method override in
    // Invis_Rectangle, due to treatment of translated(, etc.) Invis_Rectangle
    // (see is_invis_rect() below)

    // NOTE: code here is directly informed by the usage of Invis_Rectangle in 
    // _._._.tile.adjust.Drawing_Shape_Adjustment_Methods.pad()--namely it is
    // neither scaled, rotated, reflected, skewed, nor inked, and is always
    // `under` in a composite

    def is_invis_rect(shape: Drawing_Shape): Boolean =
      shape match {
        case Invis_Rectangle(_, _)         => true
        case Translated_Shape(inner, _, _) => is_invis_rect(inner)
        // no Invis_Rectangle is expected to be scaled, yet don't overlook here
        case Scaled_Shape(inner, _, _)     => is_invis_rect(inner)
        // thanks to this method, no invis rect shall be within nested composite
        //   case Composite_Shape(under, over)
        // no Invis_Rectangle is expected to be rotated, reflected, skewed, nor
        // inked-- stand by this!
        //   case Rotated_Shape(inner, _, _, _)
        //   case Reflected_Shape(inner, _, _, _)
        //   case Skewed_Horiz_Shape(inner, _)
        //   case Skewed_Vert_Shape(inner, _)
        //   case Inked_Shape(_, _)


//??????????what about Clipped_Shape??????????????

//??????????what about Masked_Shape??????????????

        case _ => false
      }

    def merge_invis_rects(r1: Drawing_Shape, r2: Drawing_Shape): Drawing_Shape =
      // NOTE: crucial to combo, not each shape, but each shape's bounding_box,
      // to eliminate potential for infinite recursion, since
      // (Drawing_Shape).combo implemented ITO this very method
      // Invis_Rectangle.cloak_rect((r1 -& r2).bounding_box.as_drawing_shape)
      Invis_Rectangle.cloak_rect((r1.bounding_box -& r2.bounding_box).
                                   as_drawing_shape)

    if (is_invis_rect(this)) {
      other match {
        case Composite_Shape(under, over) if is_invis_rect(under) =>
          Composite_Shape(merge_invis_rects(this, under), over)
        case _ if is_invis_rect(other) =>
          merge_invis_rects(this, other)
        case _ =>
          Composite_Shape(this, other)
      }
    } else {
      other match {
        case Identity_Shape() => this // special handling for 'identity' shape
        case _                => Composite_Shape(this, other)
      }
    }
  }

  protected def create_clipped_shape(clipping: Drawing_Shape, rule: Clip_Rule):
      Drawing_Shape =
    clipping match {
      case Identity_Shape() => Null_Shape // Identity_Shape clips everything
      case _                => Clipped_Shape(this, clipping, rule)
    }

  protected def create_masked_shape(mask: Drawing_Shape): Drawing_Shape =
    mask match {
      case Identity_Shape() => Null_Shape // Identity_Shape masks everything
      case _                => Masked_Shape(this, mask)
    }

  protected def create_inked_shape(pen: Pen): Drawing_Shape =
    Inked_Shape(this, pen)

  protected def create_effected_shape(effect: Effect): Drawing_Shape =
    effect match {
      case Opacity_Effect(opacity) => this match {
        case Non_Opaque_Shape(shape, prev_opacity) =>
          val resulting_opacity = prev_opacity * opacity
          if (resulting_opacity == 1.0)
            shape
          else
            Non_Opaque_Shape(shape, resulting_opacity)
        case _ =>
            Non_Opaque_Shape(this, opacity)
      }
      case Filter_Effect(filter)   => Filtered_Shape(this, filter)
    }

  protected def create_attributed_shape(attribution: Attribution):
      Drawing_Shape =
    Attributed_Shape(this, attribution)
}


sealed abstract class True_Drawing_Shape
    extends Drawing_Shape { self: Shape_Op =>

  def as_path: Path
}


sealed abstract class Faux_Drawing_Shape
    extends Drawing_Shape with Nullary_Shape_Op

// for 'best' bounding shape (where shape must be necessarily 'closed')
sealed abstract class Pre_Formulated_Shape
    extends True_Drawing_Shape {  self: Shape_Op =>

//     with Transforming[Pre_Formulated_Shape]
//     with Placeable[Pre_Formulated_Shape] {


  def as_segments: Seq[Segment] =
    Nil //!!!!!!!!!!this should be fully abstract, not defined here!!!!!!!

  override
  def best_bounding_shape: Pre_Formulated_Shape =
    this


/*?????it would seem to be equivalent to 'override' every method to have a
  covariant return type by extending `Transforming[Pre_Formulated_Shape]`
  however it causes the following error, which seems only to be solved by
  overriding every method 'by hand' to specifically state the covariant return
  type--what could be done here???????

[ERROR] .../tie/tie/src/main/scala/k_k_/graphics/tie/shapes.scala:1096: error: illegal inheritance;
[INFO]  class Rotated_Pre_Formulated_Shape inherits different type instances of trait Transforming:
[INFO] k_k_.graphics.tie.transformable.Transforming[k_k_.graphics.tie.shapes.Pre_Formulated_Shape] and k_k_.graphics.tie.transformable.Transforming[k_k_.graphics.tie.shapes.Drawing_Shape]
[INFO] final case class Rotated_Pre_Formulated_Shape(shape: Pre_Formulated_Shape,
[INFO]                  ^


  override type Translated_T        = Translated_Pre_Formulated_Shape
  override protected val Translated = Translated_Pre_Formulated_Shape

  override type Scaled_T            = Scaled_Pre_Formulated_Shape
  override protected val Scaled     = Scaled_Pre_Formulated_Shape

  override type Rotated_T           = Rotated_Pre_Formulated_Shape
  override protected val Rotated    = Rotated_Pre_Formulated_Shape

  override type Reflected_T         = Reflected_Pre_Formulated_Shape
  override protected val Reflected  = Reflected_Pre_Formulated_Shape
*/


  override
  def move(x_dist: Double, y_dist: Double): Pre_Formulated_Shape = {
    this match {
      case Translated_Pre_Formulated_Shape(inner,
                                           existing_x_dist, existing_y_dist) =>
        val combined_x_dist = x_dist + existing_x_dist
        val combined_y_dist = y_dist + existing_y_dist
        if (combined_x_dist == 0.0 && combined_y_dist == 0.0)
          inner // successive ops cancel one another
        else
          // adjust 'previous' op by combining with sucessor
          Translated_Pre_Formulated_Shape(inner,
                                          combined_x_dist, combined_y_dist)
      case _ =>
        Translated_Pre_Formulated_Shape(this, x_dist, y_dist)
    }
  }

  override
  def move(pt_offset: Point): Pre_Formulated_Shape =
    move(pt_offset.x, pt_offset.y)

  override
  def move(dist: Double): Pre_Formulated_Shape =
    move(dist, dist)

  override
  def -+(x_dist: Double, y_dist: Double): Pre_Formulated_Shape =
    move(x_dist, y_dist)

  override
  def -+(pt_offset: Point): Pre_Formulated_Shape =
    move(pt_offset.x, pt_offset.y)

  override
  def -+(dist: Double): Pre_Formulated_Shape =
    move(dist, dist)


  override
  def move_@(dest_pt: Point): Pre_Formulated_Shape =
    move(dest_pt -+ -center_pt)

  override
  def move_@(x_coord: Double, y_coord: Double): Pre_Formulated_Shape =
    move_@(Point(x_coord, y_coord))

  override
  def move_abs(dest_pt: Point): Pre_Formulated_Shape = move_@(dest_pt)

  override
  def move_abs(x_coord: Double, y_coord: Double): Pre_Formulated_Shape =
    move_@(x_coord, y_coord)

  override
  def -+@(dest_pt: Point): Pre_Formulated_Shape =
    move_@(dest_pt)

  override
  def -+@(x_coord: Double, y_coord: Double): Pre_Formulated_Shape =
    move_@(Point(x_coord, y_coord))


  override
  def scale(x_scaling: Double, y_scaling: Double):
      Pre_Formulated_Shape = {
    this match {
      case Scaled_Pre_Formulated_Shape(inner,
                                       existing_x_scaling,existing_y_scaling) =>
        val combined_x_scaling = x_scaling * existing_x_scaling
        val combined_y_scaling = y_scaling * existing_y_scaling
        if (combined_x_scaling == 1.0 && combined_y_scaling == 1.0)
          inner // successive ops cancel one another
        else
          // adjust 'previous' op by combining with sucessor
          Scaled_Pre_Formulated_Shape(inner,
                                      combined_x_scaling, combined_y_scaling)
      case _ =>
        Scaled_Pre_Formulated_Shape(this, x_scaling, y_scaling)
    }
  }


  override
  def scale(scaling: Double): Pre_Formulated_Shape =
    scale(scaling, scaling)

  override
  def -*(x_scaling: Double, y_scaling: Double): Pre_Formulated_Shape =
    scale(x_scaling, y_scaling)

  override
  def -*(scaling: Double): Pre_Formulated_Shape =
    scale(scaling, scaling)


  override
  def rotate(degrees: Double, about_x: Double, about_y: Double):
      Pre_Formulated_Shape = {
    (about_x, about_y, this) match {
      // NOTE: simplify only when both rotate about (0,0)--else too complicated
      case (0, 0, Rotated_Pre_Formulated_Shape(inner, existing_degrees, 0, 0))=>
        val combined_degrees = degrees + existing_degrees
        if (combined_degrees % 360 == 0.0)
          inner // successive ops cancel one another
        else
          // adjust 'previous' op by combining with sucessor
          Rotated_Pre_Formulated_Shape(inner, combined_degrees, 0, 0)
      case _ =>
        Rotated_Pre_Formulated_Shape(this, degrees, about_x, about_y)
    }
  }

  override
  def rotate(degrees: Double, center_pt: Point = Point(0, 0)):
      Pre_Formulated_Shape =
    rotate(degrees, center_pt.x, center_pt.y)

  override
  def -%(degrees: Double, about_x: Double, about_y: Double):
      Pre_Formulated_Shape =
    rotate(degrees, about_x, about_y)

  override
  def -%(degrees: Double, center_pt: Point = Point(0, 0)):
      Pre_Formulated_Shape =
    rotate(degrees, center_pt.x, center_pt.y)


  override
  def reflect(degrees: Double, about_x: Double, about_y: Double):
      Pre_Formulated_Shape = {
    (about_x, about_y, this) match {
      // NOTE: simplify only when both reflect about (0,0)--else too complicated
      case (0, 0, Reflected_Pre_Formulated_Shape(inner, existing_degrees, 0, 0))
             if ((degrees % 360) == (existing_degrees % 360)) =>
          inner // successive ops cancel one another
      case _ =>
        Reflected_Pre_Formulated_Shape(this, degrees, about_x, about_y)
    }
  }

  override
  def reflect(degrees: Double, about_pt: Point = Point(0, 0)):
      Pre_Formulated_Shape =
    reflect(degrees, about_pt.x, about_pt.y)

  override
  def -|-(degrees: Double, about_x: Double, about_y: Double):
      Pre_Formulated_Shape =
    reflect(degrees, about_x, about_y)

  override
  def -|-(degrees: Double, about_pt: Point = Point(0, 0)):
      Pre_Formulated_Shape =
    reflect(degrees, about_pt.x, about_pt.y)
}


sealed abstract class Segment
    extends True_Drawing_Shape with Nullary_Shape_Op

object Line {

  def between(p1: Point, p2: Point): Drawing_Shape =
    if (p1 == p2) Line(0.001) -+@ p1
    else {
      val (run, rise) = p2 - p1
      val rotate_degrees = {
        if      (rise == 0.0) 0
        else if (run  == 0.0) if (rise > 0) 90 else 270
        else math.toDegrees(math.atan(rise / run))
      }
      val length = p1.distance(p2)
      val start: Point = (if (run < 0.0) length/2 else -length/2, 0.0)
      val (move_x, move_y) = p1 - start.rotate(rotate_degrees)
      Line(length).rotate(rotate_degrees).move(move_x, move_y)
    }
}

final case class Line(length: Double)
    extends Segment {

  def bounding_box =
    Origin_Ortho_Rectangle(length, 0.01)

  def as_path = {
    //????NOTE: the change from scala 2.7.7.RC2 to 2.8.1 seemed to break the
    // following, giving: 'error: type mismatch;
    // [INFO]  found   : (Double, Int)
    // [INFO]  required: k_k_.graphics.tie.shapes.Point'
    // the implicit is: implicit def from_double2(x_and_y: (Double, Double)) =
    //????prior to 2.8.1 Int must have been auto-converted to Double; did the
    // language spec change, or is this a bug????
    //...pper_left: Point = (0 - length/2, 0)
    val upper_left: Point = (0 - length/2, 0.0)
    Path.from(upper_left).
         horiz(length)
         // NOTE: no `close`, since overstroke may double effective stroke-width
  }
}


final case class Hemisphere(rad_width: Double, rad_height: Double,
                            top_? : Boolean = true)
    extends Segment {

  def bounding_box =
    Origin_Ortho_Rectangle(rad_width * 2, rad_height)

  def as_path = {
    val left_start:  Point = (0 - rad_width,
                              if (top_?) rad_height/2 else 0 - rad_height/2)
    Path.from(left_start).
         arc(rad_width, rad_height, if (top_?) Large_CW else Large_CCW,
             rad_width * 2, 0)
  }
}

object Iso_Triangle {

  def apply(base_width: Double, height: Double) =
    new Iso_Triangle(base_width, height)

  def unapply(iso_tri: Iso_Triangle) =
    Some(iso_tri.base_width, iso_tri.height)
}

sealed class Iso_Triangle(val base_width: Double, val height: Double)
    extends Pre_Formulated_Shape with Nullary_Shape_Op {

  def bounding_box =
    Origin_Ortho_Rectangle(base_width, height)

  def as_path = {
    val top_point: Point = (0.0, 0 - height/2) 
    val (slant_width, slant_height) = (base_width/2, height) 
    Path.from(top_point).
         line(slant_width, slant_height).
         horiz(-base_width).
         close
  }

  override def toString =
    "Iso_Triangle(" + base_width + "," + height + ")"
}

final case class Right_Triangle(base_width: Double, height: Double)
    extends Pre_Formulated_Shape with Nullary_Shape_Op {

  def bounding_box =
    Origin_Ortho_Rectangle(base_width, height)

  def as_path = {
    val top_point: Point = (0 + base_width/2, 0 - height/2) 
    Path.from(top_point).
         vert(height).
         horiz(-base_width).
         close
  }
}

object Rectangle {

  def apply(width: Double, height: Double) =
    new Rectangle(width, height)

  def unapply(rect: Rectangle) =
    Some(rect.width, rect.height)
}

sealed class Rectangle(val width: Double, val height: Double)
    extends Pre_Formulated_Shape with Rectangular with Nullary_Shape_Op {

  def bounding_box =
    Origin_Ortho_Rectangle(width, height)

  def as_path = {
    val upper_left: Point = (0 - width/2, 0 - height/2)
    Path.from(upper_left).
         horiz( width).vert( height).
         horiz(-width).vert(-height). //???delete vert????
         close
  }

  override def toString =
    "Rectangle(" + width + "," + height + ")"
}

final case class Parallelogram(side_width: Double, full_width: Double,
                               height: Double)
    extends Pre_Formulated_Shape with Nullary_Shape_Op {

  def bounding_box =
    Origin_Ortho_Rectangle(full_width, height)

  def as_path = {
  //??????is rearranging these a good idea or not?????
    val (long_width, short_width) = More_Math.max_min(side_width, full_width)
    val slant_width = long_width - short_width
    val upper_left: Point = (0 - (long_width/2 - slant_width),
                             0 - height/2) 
    Path.from(upper_left).
         horiz( short_width).
         line(-slant_width, height).
         horiz(-short_width).
         close
  }
}

final case class Trapezoid(top_width: Double, bottom_width: Double,
                           height: Double)
    extends Pre_Formulated_Shape with Nullary_Shape_Op {

  def bounding_box =
    Origin_Ortho_Rectangle(math.max(top_width, bottom_width), height)

  def as_path = {
    val upper_left: Point = (0 - top_width/2, 0 - height/2) 
    val (slant_width, slant_height) = ((bottom_width - top_width) / 2, height)
    Path.from(upper_left).
         horiz(top_width).
         line(slant_width, slant_height).
         horiz(-bottom_width).
         close
  }
}

object Pentagon {

  def apply(side_width: Double, full_width: Double,
            side_height: Double, full_height: Double) =
    new Pentagon(side_width, full_width, side_height, full_height)

  def unapply(pent: Pentagon) =
    Some(pent.side_width, pent.full_width, pent.side_height, pent.full_height)
}

sealed class Pentagon(val side_width: Double,  val full_width: Double,
                      val side_height: Double, val full_height: Double)
    extends Pre_Formulated_Shape with Nullary_Shape_Op {

  def bounding_box =
    Origin_Ortho_Rectangle(full_width, full_height)

  def as_path = {
    val top_point: Point = (0.0, 0 - full_height/2)
    val (tip_slant_width, tip_slant_height) = (full_width / 2,
                                               full_height - side_height)
    val (base_slant_width, base_slant_height) = ((full_width - side_width) / 2,
                                                 side_height)
    Path.from(top_point).
         line( tip_slant_width,   tip_slant_height).
         line(-base_slant_width,  base_slant_height).
         horiz(-side_width).
         line(-base_slant_width, -base_slant_height).
         close
  }

  override def toString =
    "Pentagon(" + side_width  + "," + full_width + "," +
                  side_height + "," + full_height + ")"
}

object Hexagon {

  def apply(side_width: Double, full_width: Double, height: Double) =
    new Hexagon(side_width, full_width, height)

  def unapply(hex: Hexagon) =
    Some(hex.side_width, hex.full_width, hex.height)
}

sealed class Hexagon(val side_width: Double, val full_width: Double,
                     val height: Double)
    extends Pre_Formulated_Shape with Nullary_Shape_Op {

  def bounding_box =
    Origin_Ortho_Rectangle(full_width, height)

  def as_path = {
    val upper_left: Point = (0 - side_width/2, 0 - height/2)
      //?????????possibly rearrange args to use larger of two?????????
    val (slant_width, slant_height) = ((full_width - side_width) / 2,
                                       height / 2)
    Path.from(upper_left).
         horiz( side_width).line( slant_width,  slant_height).
                            line(-slant_width,  slant_height).
         horiz(-side_width).line(-slant_width, -slant_height).
         close
  }

  override def toString =
    "Hexagon(" + side_width  + "," + full_width + "," + height + ")"
}

object Octagon {

  def apply(side_width: Double, full_width: Double,
            side_height: Double, full_height: Double) =
    new Octagon(side_width, full_width, side_height, full_height)

  def unapply(oct: Octagon) =
    Some(oct.side_width, oct.full_width, oct.side_height, oct.full_height)
}

sealed class Octagon(val side_width: Double,  val full_width: Double,
                     val side_height: Double, val full_height: Double)
    extends Pre_Formulated_Shape with Nullary_Shape_Op {

  def bounding_box =
    Origin_Ortho_Rectangle(full_width, full_height)

  def as_path = {
    val upper_left: Point = (0 - side_width/2, 0 - full_height/2)
      //?????????possibly rearrange args to use larger of two?????????
    val (slant_width, slant_height) = ((full_width  - side_width)  / 2,
                                       (full_height - side_height) / 2)
    Path.from(upper_left).
        horiz( side_width).line( slant_width,  slant_height).vert( side_height).
                           line(-slant_width,  slant_height).
        horiz(-side_width).line(-slant_width, -slant_height).vert(-side_height).
        close
  }

  override def toString =
    "Octagon(" + side_width  + "," + full_width + "," +
                 side_height + "," + full_height + ")"
}

object Ellipse {

  def apply(rad_width: Double, rad_height: Double) =
    new Ellipse(rad_width, rad_height)

  def unapply(e: Ellipse) =
    Some(e.rad_width, e.rad_height)
}

sealed class Ellipse(val rad_width: Double, val rad_height: Double)
    extends Pre_Formulated_Shape with Nullary_Shape_Op {

  def bounding_box =
    Origin_Ortho_Rectangle(rad_width*2, rad_height*2)

  def as_path = {
    val left_start:  Point = (0 - rad_width, 0.0)
    // NOTE: for SVG 1.1, when arc coords equal current pt., the arc is NOT
    // rendered--even when choosing Large_{C}CW arc! therefore, compose ellipse
    // from top and bottom 'half' arcs, before closing.
    Path.from(left_start).
         arc(rad_width, rad_height, Large_CW,   rad_width * 2,  0).
         arc(rad_width, rad_height, Large_CW, -(rad_width * 2), 0).
         close
  }

  override def toString =
    "Ellipse(" + rad_width + "," + rad_height + ")"
}


object Free_Form {

  def apply(path: Path) =
    new Free_Form(path)

  def unapply(free_form: Free_Form) =
    Some(free_form.path)
}

sealed class Free_Form(val path: Path)
    extends True_Drawing_Shape with Nullary_Shape_Op {

  def bounding_box = {

    import Point._

    def calc_max_boundaries: (Point, Point) = {

      import scala.annotation.tailrec

      @tailrec // ensure tail-call-optimization to handle long Path cmd seqs
      def track_boundary_growth(remaining_cmds: List[Path_Cmd],
                                path_memory: Path.Pos_Memory,
                                curr_bounds: Option[(Point, Point)]):
          (Point, Point) = {
        remaining_cmds match {
          case Nil => curr_bounds match {
            case Some(bounds) => bounds
            case None => ((0.0, 0.0), (0.0, 0.0))
          }
          case cmd :: more_cmds =>
            cmd match {
              // position/path-management commands:
              case Move_Abs(x, y) =>
                track_boundary_growth(more_cmds,
                                      path_memory.start_sub_path_abs(x, y),
                                      curr_bounds)
              case Move_Rel(x, y) =>
                track_boundary_growth(more_cmds,
                                      path_memory.start_sub_path_rel(x, y),
                                      curr_bounds)
              case Close =>
                track_boundary_growth(more_cmds,
                                      path_memory.close_sub_path,
                                      curr_bounds)

              // line-drawing commands:
              case Line_Abs(x, y) =>
                val new_path_memory = path_memory.replace_pt_abs(x, y)
                val new_bounds = record_line(path_memory.curr_pt,
                                             new_path_memory, curr_bounds)
                track_boundary_growth(more_cmds, new_path_memory, new_bounds)
              case Line_Rel(x, y) =>
                val new_path_memory = path_memory.replace_pt_rel(x, y)
                val new_bounds = record_line(path_memory.curr_pt,
                                             new_path_memory, curr_bounds)
                track_boundary_growth(more_cmds, new_path_memory, new_bounds)
              case Horizontal_Abs(x) =>
                val new_path_memory = path_memory.replace_pt_horiz_abs(x)
                val new_bounds = record_line(path_memory.curr_pt,
                                             new_path_memory, curr_bounds)
                track_boundary_growth(more_cmds, new_path_memory, new_bounds)
              case Horizontal_Rel(x) =>
                val new_path_memory = path_memory.replace_pt_rel(x, 0)
                val new_bounds = record_line(path_memory.curr_pt,
                                             new_path_memory, curr_bounds)
                track_boundary_growth(more_cmds, new_path_memory, new_bounds)
              case Vertical_Abs(y) =>
                val new_path_memory = path_memory.replace_pt_vert_abs(y)
                val new_bounds = record_line(path_memory.curr_pt,
                                             new_path_memory, curr_bounds)
                track_boundary_growth(more_cmds, new_path_memory, new_bounds)
              case Vertical_Rel(y) =>
                val new_path_memory = path_memory.replace_pt_rel(0, y)
                val new_bounds = record_line(path_memory.curr_pt,
                                             new_path_memory, curr_bounds)
                track_boundary_growth(more_cmds, new_path_memory, new_bounds)

              // non-linear-drawing commands:




              //!!!!!!!!!for now, to keep things simple, calculate as if these merely specified a line from the current position to the cmd's end point!!!!!!!!




              case Elliptical_Arc_Abs(rad_width, rad_height, x_rotate_degrees,
                                      kind: Arc_Choice, x, y) =>
                val new_path_memory = path_memory.replace_pt_abs(x, y)
                val new_bounds = record_line(path_memory.curr_pt,
                                             new_path_memory, curr_bounds)
                track_boundary_growth(more_cmds, new_path_memory, new_bounds)
              case Elliptical_Arc_Rel(rad_width, rad_height, x_rotate_degrees,
                                      kind: Arc_Choice, x, y) =>
                val new_path_memory = path_memory.replace_pt_rel(x, y)
                val new_bounds = record_line(path_memory.curr_pt,
                                             new_path_memory, curr_bounds)
                track_boundary_growth(more_cmds, new_path_memory, new_bounds)
              case Quad_Bezier_Abs(x_ctl1, y_ctl1, x, y) =>
                val new_path_memory = path_memory.replace_pt_abs(x, y)
                val new_bounds = record_line(path_memory.curr_pt,
                                             new_path_memory, curr_bounds)
                track_boundary_growth(more_cmds, new_path_memory, new_bounds)
              case Quad_Bezier_Rel(x_ctl1, y_ctl1, x, y) =>
                val new_path_memory = path_memory.replace_pt_rel(x, y)
                val new_bounds = record_line(path_memory.curr_pt,
                                             new_path_memory, curr_bounds)
                track_boundary_growth(more_cmds, new_path_memory, new_bounds)
              case Tangent_Quad_Bezier_Abs(x, y) =>
                val new_path_memory = path_memory.replace_pt_abs(x, y)
                val new_bounds = record_line(path_memory.curr_pt,
                                             new_path_memory, curr_bounds)
                track_boundary_growth(more_cmds, new_path_memory, new_bounds)
              case Tangent_Quad_Bezier_Rel(x, y) =>
                val new_path_memory = path_memory.replace_pt_rel(x, y)
                val new_bounds = record_line(path_memory.curr_pt,
                                             new_path_memory, curr_bounds)
                track_boundary_growth(more_cmds, new_path_memory, new_bounds)
              case Cubic_Bezier_Abs(x_ctl1, y_ctl1, x_ctl2, y_ctl2, x, y) =>
                val new_path_memory = path_memory.replace_pt_abs(x, y)
                val new_bounds = record_line(path_memory.curr_pt,
                                             new_path_memory, curr_bounds)
                track_boundary_growth(more_cmds, new_path_memory, new_bounds)
              case Cubic_Bezier_Rel(x_ctl1, y_ctl1, x_ctl2, y_ctl2, x, y) =>
                val new_path_memory = path_memory.replace_pt_rel(x, y)
                val new_bounds = record_line(path_memory.curr_pt,
                                             new_path_memory, curr_bounds)
                track_boundary_growth(more_cmds, new_path_memory, new_bounds)
              case Tangent_Cubic_Bezier_Abs(x_ctl1, y_ctl1, x, y) =>
                val new_path_memory = path_memory.replace_pt_abs(x, y)
                val new_bounds = record_line(path_memory.curr_pt,
                                             new_path_memory, curr_bounds)
                track_boundary_growth(more_cmds, new_path_memory, new_bounds)
              case Tangent_Cubic_Bezier_Rel(x_ctl1, y_ctl1, x, y) =>
                val new_path_memory = path_memory.replace_pt_rel(x, y)
                val new_bounds = record_line(path_memory.curr_pt,
                                             new_path_memory, curr_bounds)
                track_boundary_growth(more_cmds, new_path_memory, new_bounds)
            }
        }
      }

      def record_line(start_pos: Point, finish_path_memory: Path.Pos_Memory,
                      curr_bounds: Option[(Point, Point)]) = {
        val finish_pos = finish_path_memory.curr_pt
        val (line_min_x, line_max_x) =
          More_Math.min_max(start_pos.x, finish_pos.x)
        val (line_min_y, line_max_y) =
          More_Math.min_max(start_pos.y, finish_pos.y)
        curr_bounds match {
          case None =>
            Some((Point(line_min_x, line_min_y),
                  Point(line_max_x, line_max_y)))

          case Some(prev_bounds @ (Point(min_x, min_y), Point(max_x, max_y))) =>
            // NOTE: providing type for `adjustments` is most succinct way
            // to type-check: it precludes need to specify param type and to
            // explicitly construct `Point` in result of each anon. func., and
            // it enables direct use `prev_bounds` '@-alias' later in `foldLeft`
            val adjustments: List[((Point, Point)) => (Point, Point)] = List(
              { (bounds) =>
                if (line_min_x < min_x)
                  ((line_min_x, bounds._1.y), bounds._2)
                else bounds
              },
              { (bounds) =>
                if (line_max_x > max_x)
                  (bounds._1, (line_max_x, bounds._2.y))
                else bounds
              },
              { (bounds) =>
                if (line_min_y < min_y)
                  ((bounds._1.x, line_min_y), bounds._2)
                else bounds
              },
              { (bounds) =>
                if (line_max_y > max_y)
                  (bounds._1, (bounds._2.x, line_max_y))
                else bounds
              })
            val new_bounds = (prev_bounds /: adjustments) {
              (bounds, adjust) => adjust(bounds)
            }
            Some(new_bounds)
        }
      }

      val (initial_pos_memory, remaining_cmds) = path.init_pos_memory
      track_boundary_growth(remaining_cmds, initial_pos_memory, None)
    }

    val (min_pt, max_pt) = calc_max_boundaries
    Ortho_Rectangle.create_min_containing(min_pt, max_pt)
  }

  def as_path =
    path

  override def toString =
    "Free_Form(" + path + ")"
}


object Custom {

  def unapply(c: Custom) =
    Some(c.path)
}

// NOTE: intended as extension point: neither sealed nor final
class Custom protected(protected val custom_path: Path)
    extends Free_Form(custom_path)


object Writing {

  val text_ruler_factory = Default_Text_Ruler_Factory
}

final case class Writing(text: Text)
    extends Faux_Drawing_Shape {

  def bounding_box =
    text.text_bounding_box(Writing.text_ruler_factory)
}


object Image {

  def apply(path: String) =
    new Image(path)

  def apply(path: String, path_mapper: String => String) =
    new Image(path, path_mapper)

  def apply(path: String, width: Double, height: Double,
            path_mapper: (String => String) = identity) =
    new Image(path, width, height, path_mapper)

  def unapply(img: Image): Option[(String, Double, Double)] =
    Some(img.path, img.width, img.height)


  private def calc_dims(fpath: String): (Double, Double) = {
    val buffered_img = ImageIO.read(new File(fpath))
    (buffered_img.getWidth, buffered_img.getHeight)
  }

  private def verify_fpath_exists(path: String): String =
    if (new File(path).exists) path
    else throw new FileNotFoundException(path)
}

final class Image private (val path: String, dims: (Double, Double),
                           path_mapper: String => String)
    extends Faux_Drawing_Shape with Rectangular {

  def this(path: String, path_mapper: String => String) =
    this(path, Image.calc_dims(path), path_mapper)

  def this(path: String) =
    this(path, identity)

  def this(path: String, width: Double, height: Double,
           path_mapper: String => String = identity) =
    this(Image.verify_fpath_exists(path), (width, height), path_mapper)

  val width  = dims._1

  val height = dims._2

  val mapped_fpath = path_mapper(path)

  lazy val bounding_box =
    Origin_Ortho_Rectangle(width, height)

  override def toString =
    "Image(" + path + "," + dims + "," + path_mapper(path) + ")"
}


final case class Equi_Triangle(length: Double)
    extends Iso_Triangle(length, length * math.sqrt(3.0)/2)


final case class Square(length: Double)
    extends Rectangle(length, length)


object Reg_Pentagon {

  // 'golden ratio' == chords / sides (of regular pentagon)
  val phi = (1 + math.sqrt(5)) / 2

  val Phi = phi - 1 // == 1 / phi (neato!)

  def of_sides(length: Double) =
    Reg_Pentagon(length * phi)
}

import Reg_Pentagon.{phi, Phi}

final case class Reg_Pentagon(shape_width: Double)
     extends Pentagon(shape_width * Phi, shape_width,
                      (shape_width * Phi / 2) * math.sqrt(4 - Phi*Phi),
                      shape_width * Phi * math.sqrt((phi*phi) - .25))


object Reg_Hexagon {

  def of_sides(length: Double) =
    Reg_Hexagon(length * 2)
}

final case class Reg_Hexagon(shape_width: Double)
     extends Hexagon(shape_width/2, shape_width,
                     shape_width/2 * math.sqrt(3.0))


object Reg_Octagon {

  def of_sides(length: Double) =
    Reg_Octagon(length * (1 + math.sqrt(2.0)))
}

final case class Reg_Octagon(shape_width: Double)
     extends Octagon(shape_width / (1 + math.sqrt(2.0)), shape_width,
                     shape_width / (1 + math.sqrt(2.0)), shape_width)


object Circle {

  def apply(rad: Double) =
    new Circle(rad)

  def unapply(circ: Circle) =
    Some(circ.rad)
}

sealed class Circle(val rad: Double)
    extends Ellipse(rad, rad) {

  override def toString =
    "Circle(" + rad + ")"
}


object Invis_Rectangle {

  // HINT: an expensive noop--be sure `shape` actually contains a Rectangle!
  def cloak_rect(shape: Drawing_Shape): Drawing_Shape =
      shape match {
        case Rectangle(width, height) =>
          Invis_Rectangle(width, height)
        case Translated_Shape(inner, x_dist, y_dist) =>
          Translated_Shape(cloak_rect(inner), x_dist, y_dist)

        // NOTE: no Invis_Rectangle is expected to be scaled, rotated, reflected
        // skewed, inked, nor composed (in non-'under' pos); yet, for
        // robustness, implement anyway:
        case ushape @ Unary_Shape_Op(s)     => ushape.replace(cloak_rect(s))
        case bshape @ Binary_Shape_Op(l, r) => bshape.replace(cloak_rect(l),
                                                              cloak_rect(r))
        case nshape @ Nullary_Shape_Op()    =>
          nshape // ouch!--no Rectangle here, return unchanged
      }
}

sealed case class Invis_Rectangle(w: Double, h: Double)
    extends Rectangle(w, h)


// NOTE: Diam_Ellipse, Diam_Circle allow for substitutability w/ Rectangle, ala:
//     val Shape_Class = if (foo) Rectangle else Diam_Ellipse
//     val shape = Shape_Class(50, 20)

final case class Diam_Ellipse(diam_width: Double, diam_height: Double)
    extends Ellipse(diam_width/2, diam_height/2)

final case class Diam_Circle(diam: Double)
    extends Circle(diam/2)


object Translated_Shape {

  def apply(shape: Drawing_Shape, x_dist: Double, y_dist: Double) =
    Translated_Non_Pre_Formulated_Shape(shape, x_dist, y_dist)

  def unapply(shape: Drawing_Shape) =
    shape match {
      case s: Translated_Non_Pre_Formulated_Shape =>
        Some(s.shape, s.x_dist, s.y_dist)
      case s: Translated_Pre_Formulated_Shape =>
        Some(s.shape, s.x_dist, s.y_dist)
      case _ => None
    }
}

object Translated_Non_Pre_Formulated_Shape
    extends Translated_Transformable[Drawing_Shape] {

  protected def isInstanceOfCompanion(x: Any): Boolean =
    x.isInstanceOf[Translated_Non_Pre_Formulated_Shape]
}

final case class Translated_Non_Pre_Formulated_Shape(shape: Drawing_Shape,
                                                     x_dist: Double,
                                                     y_dist: Double)
    extends Drawing_Shape with Unary_Shape_Op {

  lazy val bounding_box =
    shape.bounding_box.move(x_dist, y_dist)

  def replace(replacement_shape: Drawing_Shape) =
    copy(shape = replacement_shape)
}

/*!!!not needed since not possible for Pre_Formulated_Shape to extend Transforming[Pre_Formulated_Shape]!!!

object Translated_Pre_Formulated_Shape
    extends Translated_Transformable[Pre_Formulated_Shape] {

  protected def isInstanceOfCompanion(x: Any): Boolean =
    x.isInstanceOf[Translated_Pre_Formulated_Shape]
}
*/

final case class Translated_Pre_Formulated_Shape(shape: Pre_Formulated_Shape,
                                                 x_dist: Double, y_dist: Double)
    extends Pre_Formulated_Shape with Unary_Shape_Op {

  lazy val bounding_box =
    shape.bounding_box.move(x_dist, y_dist)

  def as_path: Path =
    shape.as_path.move(x_dist, y_dist)

  def replace(replacement_shape: Drawing_Shape) =
    replacement_shape match {
      case pre_form_shape: Pre_Formulated_Shape =>
        copy(shape = pre_form_shape)
      case _ =>
        Translated_Shape(replacement_shape, x_dist, y_dist)
    }
}


object Scaled_Shape {

  def apply(shape: Drawing_Shape, x_scaling: Double, y_scaling: Double) =
    Scaled_Non_Pre_Formulated_Shape(shape, x_scaling, y_scaling)

  def unapply(shape: Drawing_Shape) =
    shape match {
      case s: Scaled_Non_Pre_Formulated_Shape =>
        Some(s.shape, s.x_scaling, s.y_scaling)
      case s: Scaled_Pre_Formulated_Shape =>
        Some(s.shape, s.x_scaling, s.y_scaling)
      case _ => None
    }
}

object Scaled_Non_Pre_Formulated_Shape
    extends Scaled_Transformable[Drawing_Shape] {

  protected def isInstanceOfCompanion(x: Any): Boolean =
    x.isInstanceOf[Scaled_Non_Pre_Formulated_Shape]
}

final case class Scaled_Non_Pre_Formulated_Shape(shape: Drawing_Shape,
                                                 x_scaling: Double,
                                                 y_scaling: Double)
    extends Drawing_Shape with Unary_Shape_Op {

  lazy val bounding_box =
    shape.bounding_box.scale(x_scaling, y_scaling)

  def replace(replacement_shape: Drawing_Shape) =
    copy(shape = replacement_shape)
}

final case class Scaled_Pre_Formulated_Shape(shape: Pre_Formulated_Shape,
                                             x_scaling: Double,
                                             y_scaling: Double)
    extends Pre_Formulated_Shape with Unary_Shape_Op {

  lazy val bounding_box =
    shape.bounding_box.scale(x_scaling, y_scaling)

  def as_path: Path =
    shape.as_path.scale(x_scaling, y_scaling)

  def replace(replacement_shape: Drawing_Shape) =
    replacement_shape match {
      case pre_form_shape: Pre_Formulated_Shape =>
        copy(shape = pre_form_shape)
      case _ =>
        Scaled_Shape(replacement_shape, x_scaling, y_scaling)
    }
}


object Rotated_Shape {

  def apply(shape: Drawing_Shape,
            degrees: Double, x_pivot: Double, y_pivot: Double) =
    Rotated_Non_Pre_Formulated_Shape(shape, degrees, x_pivot, y_pivot)

  def unapply(shape: Drawing_Shape) =
    shape match {
      case s: Rotated_Non_Pre_Formulated_Shape =>
        Some(s.shape, s.degrees, s.x_pivot, s.y_pivot)
      case s: Rotated_Pre_Formulated_Shape =>
        Some(s.shape, s.degrees, s.x_pivot, s.y_pivot)
      case _ => None
    }
}

object Rotated_Non_Pre_Formulated_Shape
    extends Rotated_Transformable[Drawing_Shape] {

  protected def isInstanceOfCompanion(x: Any): Boolean =
    x.isInstanceOf[Rotated_Non_Pre_Formulated_Shape]
}

final case class Rotated_Non_Pre_Formulated_Shape(shape: Drawing_Shape,
                                                  degrees: Double,
                                                  x_pivot: Double,
                                                  y_pivot: Double)
    extends Drawing_Shape with Unary_Shape_Op {

  lazy val bounding_box =
    shape.bounding_box.rotate(degrees, x_pivot, y_pivot)

  def replace(replacement_shape: Drawing_Shape) =
    copy(shape = replacement_shape)
}

final case class Rotated_Pre_Formulated_Shape(shape: Pre_Formulated_Shape,
                                              degrees: Double,
                                              x_pivot: Double, y_pivot: Double)
    extends Pre_Formulated_Shape with Unary_Shape_Op {

  lazy val bounding_box =
    shape.bounding_box.rotate(degrees, x_pivot, y_pivot)

  def as_path: Path =
    shape.as_path.rotate(degrees, x_pivot, y_pivot)

  def replace(replacement_shape: Drawing_Shape) =
    replacement_shape match {
      case pre_form_shape: Pre_Formulated_Shape =>
        copy(shape = pre_form_shape)
      case _ =>
        Rotated_Shape(replacement_shape, degrees, x_pivot, y_pivot)
    }
}


object Reflected_Shape {

  def apply(shape: Drawing_Shape,
            degrees: Double, x_pivot: Double, y_pivot: Double) =
    Reflected_Non_Pre_Formulated_Shape(shape, degrees, x_pivot, y_pivot)

  def unapply(shape: Drawing_Shape) =
    shape match {
      case s: Reflected_Non_Pre_Formulated_Shape =>
        Some(s.shape, s.degrees, s.x_pivot, s.y_pivot)
      case s: Reflected_Pre_Formulated_Shape =>
        Some(s.shape, s.degrees, s.x_pivot, s.y_pivot)
      case _ => None
    }
}

object Reflected_Non_Pre_Formulated_Shape
    extends Reflected_Transformable[Drawing_Shape] {

  protected def isInstanceOfCompanion(x: Any): Boolean =
    x.isInstanceOf[Reflected_Non_Pre_Formulated_Shape]
}

final case class Reflected_Non_Pre_Formulated_Shape(shape: Drawing_Shape,
                                                    degrees: Double,
                                                    x_pivot: Double,
                                                    y_pivot: Double)
    extends Drawing_Shape with Unary_Shape_Op {

  lazy val bounding_box =
    shape.bounding_box.reflect(degrees, x_pivot, y_pivot)

  def replace(replacement_shape: Drawing_Shape) =
    copy(shape = replacement_shape)
}

final case class Reflected_Pre_Formulated_Shape(shape: Pre_Formulated_Shape,
                                                degrees: Double,
                                                x_pivot: Double,y_pivot: Double)
    extends Pre_Formulated_Shape with Unary_Shape_Op {

  lazy val bounding_box =
    shape.bounding_box.reflect(degrees, x_pivot, y_pivot)

  def as_path: Path =
    shape.as_path.reflect(degrees, x_pivot, y_pivot)

  def replace(replacement_shape: Drawing_Shape) =
    replacement_shape match {
      case pre_form_shape: Pre_Formulated_Shape =>
        copy(shape = pre_form_shape)
      case _ =>
        Reflected_Shape(replacement_shape, degrees, x_pivot, y_pivot)
    }
}


object Skewed_Horiz_Shape extends Skewed_Horiz_Transformable[Drawing_Shape] {

  protected def isInstanceOfCompanion(x: Any): Boolean =
    x.isInstanceOf[Skewed_Horiz_Shape]
}

final case class Skewed_Horiz_Shape(shape: Drawing_Shape, degrees: Double)
    extends Drawing_Shape with Unary_Shape_Op {

  lazy val bounding_box =
    shape.bounding_box.skew_horiz(degrees)

  def replace(replacement_shape: Drawing_Shape) =
    copy(shape = replacement_shape)
}


object Skewed_Vert_Shape extends Skewed_Vert_Transformable[Drawing_Shape] {

  protected def isInstanceOfCompanion(x: Any): Boolean =
    x.isInstanceOf[Skewed_Vert_Shape]
}

final case class Skewed_Vert_Shape(shape: Drawing_Shape, degrees: Double)
    extends Drawing_Shape with Unary_Shape_Op {

  lazy val bounding_box =
    shape.bounding_box.skew_vert(degrees)

  def replace(replacement_shape: Drawing_Shape) =
    copy(shape = replacement_shape)
}


final case class Composite_Shape(under: Drawing_Shape, over: Drawing_Shape)
    extends Drawing_Shape with Binary_Shape_Op {

  lazy val bounding_box =
    under.bounding_box.combo(over.bounding_box)

  def left  = under
  def right = over

  def replace(replacement_left: Drawing_Shape,
              replacement_right: Drawing_Shape) =
    copy(under = replacement_left, over = replacement_right)
}

final case class Clipped_Shape(clipped: Drawing_Shape, clipping: Drawing_Shape, 
                               rule: Clip_Rule)
    extends Drawing_Shape with Binary_Shape_Op {

  lazy val bounding_box =
    clipped.bounding_box.clip_by(clipping.bounding_box)

  def left  = clipped
  def right = clipping

  def replace(replacement_left: Drawing_Shape,
              replacement_right: Drawing_Shape) =
    copy(clipped = replacement_left, clipping = replacement_right)
}

final case class Masked_Shape(masked: Drawing_Shape, mask: Drawing_Shape)
    extends Drawing_Shape with Binary_Shape_Op {

  lazy val bounding_box =
    masked.bounding_box.mask_by(mask.bounding_box)

  def left  = masked
  def right = mask

  override
  def best_bounding_shape: Pre_Formulated_Shape =
    masked.best_bounding_shape

  def replace(replacement_left: Drawing_Shape,
              replacement_right: Drawing_Shape) =
    copy(masked = replacement_left, mask = replacement_right)
}

final case class Inked_Shape(shape: Drawing_Shape, pen: Pen)
    extends Drawing_Shape with Unary_Shape_Op {

  lazy val bounding_box =
    shape.bounding_box.using(pen)

  override
  def best_bounding_shape: Pre_Formulated_Shape =
    shape.best_bounding_shape

  def replace(replacement_shape: Drawing_Shape) =
    copy(shape = replacement_shape)
}

final case class Non_Opaque_Shape(shape: Drawing_Shape, opacity: Double)
    extends Drawing_Shape with Unary_Shape_Op {

  lazy val bounding_box =
    shape.bounding_box.exhibit(Opacity_Effect(opacity))

  override
  def best_bounding_shape: Pre_Formulated_Shape =
    shape.best_bounding_shape

  def replace(replacement_shape: Drawing_Shape) =
    copy(shape = replacement_shape)
}

final case class Filtered_Shape(shape: Drawing_Shape, filter: Filter)
    extends Drawing_Shape with Unary_Shape_Op {

  lazy val bounding_box =
    shape.bounding_box.exhibit(Filter_Effect(filter))

  override
  def best_bounding_shape: Pre_Formulated_Shape =
    shape.best_bounding_shape

  def replace(replacement_shape: Drawing_Shape) =
    copy(shape = replacement_shape)
}

final case class Attributed_Shape(shape: Drawing_Shape,
                                  attribution: Attribution)
    extends Drawing_Shape with Unary_Shape_Op {

  lazy val bounding_box =
    shape.bounding_box.as(attribution)

  override
  def best_bounding_shape: Pre_Formulated_Shape =
    shape.best_bounding_shape

  def replace(replacement_shape: Drawing_Shape) =
    copy(shape = replacement_shape)
}


object Ortho_Rectangle {

  def unapply(o: Ortho_Rectangle) =
    Some(o.width, o.height)

  def create_min_containing(pt1: Point, pt2: Point): Ortho_Rectangle = {
    val (min_x, max_x) = More_Math.min_max(pt1.x, pt2.x)
    val (min_y, max_y) = More_Math.min_max(pt1.y, pt2.y)
    val (width, height) = (if (max_x == min_x) 0.001 else max_x - min_x,
                           if (max_y == min_y) 0.001 else max_y - min_y)
    val ctr_pt: Point = (max_x - width/2, max_y - height/2)
    Origin_Ortho_Rectangle(width, height) move (ctr_pt.x, ctr_pt.y)
  }
}


sealed abstract class Ortho_Rectangle
    extends Transforming[Ortho_Rectangle]
       with Placeable[Ortho_Rectangle]
       with Presentable_Shape[Ortho_Rectangle]
       with Bounding_Boxed with Rectangular {

  def bounding_box =
    this

  def as_drawing_shape: Pre_Formulated_Shape // derived class of `Drawing_Shape`

  type Translated_T          = Translated_Ortho_Rectangle
  protected val Translated   = Translated_Ortho_Rectangle

  type Scaled_T              = Scaled_Ortho_Rectangle
  protected val Scaled       = Scaled_Ortho_Rectangle

  type Rotated_T             = Rotated_Ortho_Rectangle
  protected val Rotated      = Rotated_Ortho_Rectangle

  type Reflected_T           = Reflected_Ortho_Rectangle
  protected val Reflected    = Reflected_Ortho_Rectangle

  type Skewed_Horiz_T        = Skewed_Horiz_Ortho_Rectangle
  protected val Skewed_Horiz = Skewed_Horiz_Ortho_Rectangle

  type Skewed_Vert_T         = Skewed_Vert_Ortho_Rectangle
  protected val Skewed_Vert  = Skewed_Vert_Ortho_Rectangle


  protected def create_composite_shape(other: Ortho_Rectangle):
      Ortho_Rectangle = {
    val (Ortho_Rectangle(w1, h1), Ortho_Rectangle(w2, h2)) = (this, other)
    val bb1_ctr = this.center_pt
    val bb2_ctr = other.center_pt
    val (half_w1, half_h1) = (w1/2, h1/2)
    val (half_w2, half_h2) = (w2/2, h2/2)
    val (bb1_min_x, bb1_max_x, bb1_min_y, bb1_max_y) =
      (bb1_ctr.x - half_w1, bb1_ctr.x + half_w1,
       bb1_ctr.y - half_h1, bb1_ctr.y + half_h1)
    val (bb2_min_x, bb2_max_x, bb2_min_y, bb2_max_y) =
      (bb2_ctr.x - half_w2, bb2_ctr.x + half_w2,
       bb2_ctr.y - half_h2, bb2_ctr.y + half_h2)
    val (min_x, max_x, min_y, max_y) =
      (math.min(bb1_min_x, bb2_min_x), math.max(bb1_max_x, bb2_max_x),
       math.min(bb1_min_y, bb2_min_y), math.max(bb1_max_y, bb2_max_y))
    Ortho_Rectangle.create_min_containing((min_x, min_y), (max_x, max_y))
  }

  protected def create_clipped_shape(clipping: Ortho_Rectangle,
                                     rule: Clip_Rule): Ortho_Rectangle =
    // since `clipping` delimits what is shown of `this`, return that instead
    clipping

  protected def create_masked_shape(mask: Ortho_Rectangle): Ortho_Rectangle =
    // since `mask` delimits what is shown of `this`, return that instead
    mask

  protected def create_inked_shape(pen: Pen): Ortho_Rectangle = {
    // do not apply ink, merely return adjusted for size

    //!!!!this won't work for text, since stroke doesn't surround the figure!!!
    //!!!!!it may also fail when there is a nested pen has already enlarged the bounding box: containing pens would only change the color, not add more size!!!

    pen.stroke_width match {
      case Some(thickness) =>
        val (width_scale, height_scale) =
          ((width + thickness)  / width,
           (height + thickness) / height)
        this.scale(width_scale, height_scale)
      case None =>
        this
    }
  }

  protected def create_effected_shape(effect: Effect): Ortho_Rectangle =
    // since effects alter the underlying shape in a (complicated) way that
    // always leaves its dimensions intact, return `this` unchanged
    this

  protected def create_attributed_shape(attribution: Attribution):
      Ortho_Rectangle =
    // attribution has no effect on bounding box
    this
}

final case class Origin_Ortho_Rectangle(width: Double, height: Double)
    extends Ortho_Rectangle {

  override
  def center_pt =
    (0.0, 0.0)

  def as_drawing_shape: Pre_Formulated_Shape =
    Rectangle(width, height)
}

object Translated_Ortho_Rectangle
    extends Translated_Transformable[Ortho_Rectangle] {

  protected def isInstanceOfCompanion(x: Any): Boolean =
    x.isInstanceOf[Translated_Ortho_Rectangle]
}

final case class Translated_Ortho_Rectangle(rect: Ortho_Rectangle,
                                            x_dist: Double, y_dist: Double)
    extends Ortho_Rectangle {

  lazy val width  = rect.width
  lazy val height = rect.height

  override
  lazy val center_pt =
    rect.center_pt -+ (x_dist, y_dist)

  def as_drawing_shape: Pre_Formulated_Shape =
    rect.as_drawing_shape.move(x_dist, y_dist)
}

object Scaled_Ortho_Rectangle
    extends Scaled_Transformable[Ortho_Rectangle] {

  protected def isInstanceOfCompanion(x: Any): Boolean =
    x.isInstanceOf[Scaled_Ortho_Rectangle]
}

final case class Scaled_Ortho_Rectangle(rect: Ortho_Rectangle,
                                        x_scaling: Double, y_scaling: Double)
    extends Ortho_Rectangle {

  lazy val width  = rect.width  * x_scaling
  lazy val height = rect.height * y_scaling

  override
  lazy val center_pt =
    rect.center_pt -* (x_scaling, y_scaling)

  def as_drawing_shape: Pre_Formulated_Shape =
    rect.as_drawing_shape.scale(x_scaling, y_scaling)
}


trait Ortho_Rectangle_Displaceable_Points {

  protected def calc_displacement(rect: Ortho_Rectangle): Ortho_Rectangle = {
    val rect_ctr = rect.center_pt
    val (rect_half_w, rect_half_h) = (rect.width/2, rect.height/2)
    val rect_corner_pts: List[Point] =
      List((rect_ctr.x - rect_half_w, rect_ctr.y - rect_half_h),
           (rect_ctr.x - rect_half_w, rect_ctr.y + rect_half_h),
           (rect_ctr.x + rect_half_w, rect_ctr.y - rect_half_h),
           (rect_ctr.x + rect_half_w, rect_ctr.y + rect_half_h))
    val displaced_corner_pts = rect_corner_pts map calc_displacement _
    val (min_x, max_x) = (displaced_corner_pts map { _.x } min,
                          displaced_corner_pts map { _.x } max)
    val (min_y, max_y) = (displaced_corner_pts map { _.y } min,
                          displaced_corner_pts map { _.y } max)
    Ortho_Rectangle.create_min_containing((min_x, min_y), (max_x, max_y))
  }

  protected def calc_displacement(pt: Point): Point
}


object Rotated_Ortho_Rectangle
    extends Rotated_Transformable[Ortho_Rectangle] {

  protected def isInstanceOfCompanion(x: Any): Boolean =
    x.isInstanceOf[Rotated_Ortho_Rectangle]

//????for some reason the following is not equiv to the above:
//  protected val isInstanceOfCompanion =
//    (_: Any).isInstanceOf[Rotated_Ortho_Rectangle]
//
//[ERROR] .../tie/tie/src/main/scala/k_k_/graphics/tie/shapes.scala:1328: error: object creation impossible, since method isInstanceOfCompanion in trait Rotated_Transformable of type (x: Any)Boolean is not defined
//[INFO] object Rotated_Ortho_Rectangle
//[INFO]        ^
}

final case class Rotated_Ortho_Rectangle(rect: Ortho_Rectangle,
                                         degrees: Double,
                                         x_pivot: Double, y_pivot: Double)
    extends Ortho_Rectangle
       with Ortho_Rectangle_Displaceable_Points {

  lazy val width  = equiv_bounding_box.width
  lazy val height = equiv_bounding_box.height

  override
  def bounding_box =
    equiv_bounding_box

  override
  def center_pt =
    equiv_bounding_box.center_pt

  def as_drawing_shape: Pre_Formulated_Shape =
    equiv_bounding_box.as_drawing_shape


  protected def calc_displacement(pt: Point): Point =
    pt.rotate(degrees, x_pivot, y_pivot)

  private lazy val equiv_bounding_box = calc_displacement(rect)
}

object Reflected_Ortho_Rectangle
    extends Reflected_Transformable[Ortho_Rectangle] {

  protected def isInstanceOfCompanion(x: Any): Boolean =
    x.isInstanceOf[Reflected_Ortho_Rectangle]
}

final case class Reflected_Ortho_Rectangle(rect: Ortho_Rectangle,
                                           degrees: Double,
                                           x_pivot: Double, y_pivot: Double)
    extends Ortho_Rectangle
       with Ortho_Rectangle_Displaceable_Points {

  lazy val width  = equiv_bounding_box.width
  lazy val height = equiv_bounding_box.height

  override
  def bounding_box =
    equiv_bounding_box

  override
  def center_pt =
    equiv_bounding_box.center_pt

  def as_drawing_shape: Pre_Formulated_Shape =
    equiv_bounding_box.as_drawing_shape


  protected def calc_displacement(pt: Point): Point =
    pt.reflect(degrees, x_pivot, y_pivot)

  private lazy val equiv_bounding_box = calc_displacement(rect)
}

object Skewed_Horiz_Ortho_Rectangle
    extends Skewed_Horiz_Transformable[Ortho_Rectangle] {

  protected def isInstanceOfCompanion(x: Any): Boolean =
    x.isInstanceOf[Skewed_Horiz_Ortho_Rectangle]
}

final case class Skewed_Horiz_Ortho_Rectangle(rect: Ortho_Rectangle,
                                              degrees: Double)
    extends Ortho_Rectangle
       with Ortho_Rectangle_Displaceable_Points {

  lazy val width  = equiv_bounding_box.width
  lazy val height = equiv_bounding_box.height

  override
  def bounding_box =
    equiv_bounding_box

  override
  def center_pt =
    equiv_bounding_box.center_pt

  def as_drawing_shape: Pre_Formulated_Shape =
    equiv_bounding_box.as_drawing_shape


  protected def calc_displacement(pt: Point): Point =
    pt.skew_horiz(degrees)

  private lazy val equiv_bounding_box = calc_displacement(rect)
}

object Skewed_Vert_Ortho_Rectangle
    extends Skewed_Vert_Transformable[Ortho_Rectangle] {

  protected def isInstanceOfCompanion(x: Any): Boolean =
    x.isInstanceOf[Skewed_Vert_Ortho_Rectangle]
}

final case class Skewed_Vert_Ortho_Rectangle(rect: Ortho_Rectangle,
                                             degrees: Double)
    extends Ortho_Rectangle
       with Ortho_Rectangle_Displaceable_Points {

  lazy val width  = equiv_bounding_box.width
  lazy val height = equiv_bounding_box.height

  override
  def bounding_box =
    equiv_bounding_box

  override
  def center_pt =
    equiv_bounding_box.center_pt

  def as_drawing_shape: Pre_Formulated_Shape =
    equiv_bounding_box.as_drawing_shape


  protected def calc_displacement(pt: Point): Point =
    pt.skew_vert(degrees)

  private lazy val equiv_bounding_box = calc_displacement(rect)
}

}


package object shapes {

import k_k_.graphics.tie.effects.Effect
import k_k_.graphics.tie.ink.Pen
import k_k_.graphics.tie.shapes.path.Path


object Identity_Shape {

  def unapply(shape: Drawing_Shape): Boolean =
    shape eq Null_PF_Shape
}

// NOTE: define as `val` in package object rather than as object, to avoid need
// to always down-qualify to generalized type; e.g. to simplify the following:
//    ((Null_Shape: Drawing_Shape) /: shapes_seq) ( _ -& _ )
//   to:
//    (Null_Shape /: shapes_seq) ( _ -& _ )
// case object Null_Shape extends Invis_Rectangle(0.0001, 0.0001) {

val Null_PF_Shape: Pre_Formulated_Shape =
    new Pre_Formulated_Shape with Nullary_Shape_Op {

  val (alleged_width, alleged_height) = (0.0001, 0.0001)

  def bounding_box =
    Origin_Ortho_Rectangle(alleged_width, alleged_height)

  def as_path =
    Path.from(0, 0).
         close

  override def toString =
    "Null_Shape"


  // there is only one Null_Shape, which is constant under every transform...

  override
  def move(x_dist: Double, y_dist: Double):
      Pre_Formulated_Shape =
    this

  override
  def scale(x_scaling: Double, y_scaling: Double):
      Pre_Formulated_Shape =
    this

  override
  def rotate(degrees: Double, about_x: Double, about_y: Double):
      Pre_Formulated_Shape =
    this

  override
  def reflect(degrees: Double, about_x: Double, about_y: Double):
      Pre_Formulated_Shape =
    this

  override
  def skew_horiz(degrees: Double): Drawing_Shape =
    this

  override
  def skew_vert(degrees: Double): Drawing_Shape =
    this


  // ...results in an identity when combined with another shape

  override
  protected def create_composite_shape(other: Drawing_Shape): Drawing_Shape =
    other

  // ...clipped and masked with no effect

  override
  protected def create_clipped_shape(clipping: Drawing_Shape, rule: Clip_Rule):
      Drawing_Shape =
    this

  override
  protected def create_masked_shape(mask: Drawing_Shape): Drawing_Shape =
    this

  // ...unchanged under the pen

  override
  protected def create_inked_shape(pen: Pen): Drawing_Shape =
    this

  // ...impervious to every Effect

  override
  protected def create_effected_shape(effect: Effect): Drawing_Shape =
    this

  // ...and likewise unattributable

  override
  protected def create_attributed_shape(attribution: Attribution):
      Drawing_Shape =
    this
}

val Null_Shape: Drawing_Shape = Null_PF_Shape

}


/*
    (ugly, messy!) snippet of eariler version of Invis_Rectangle.cloak_rect(),
    prior to definition of Shape_Op:

        case Scaled_Shape(inner, x_scaling, y_scaling) =>
          Scaled_Shape(cloak_rect(inner), x_scaling, y_scaling)
        case Rotated_Shape(inner, degrees, x_pivot, y_pivot) =>
          Rotated_Shape(cloak_rect(inner), degrees, x_pivot, y_pivot)
        case Composite_Shape(under, over) =>
          Composite_Shape(cloak_rect(under), cloak_rect(over))
          //????????
        case Clipped_Shape(clipped, clipping, rule) =>
          Clipped_Shape(cloak_rect(clipped), cloak_rect(clipping), rule)
        case Masked_Shape(masked, mask) =>
          Masked_Shape(cloak_rect(masked), cloak_rect(mask))
        case Inked_Shape(inner, pen) =>
          Inked_Shape(cloak_rect(inner), pen)
        case x @ ( _ : Segment | _ : Pre_Formulated_Shape | _ : Free_Form |
                   _ : Writing) =>
          shape // ouch!--no Rectangle here, return unchanged
*/
