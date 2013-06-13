package k_k_.graphics.tie.fmt

import k_k_.graphics.tie._
import k_k_.graphics.tie.Renderer
import k_k_.graphics.tie.effects.Filter
import k_k_.graphics.tie.ink._
import k_k_.graphics.tie.shapes._
import k_k_.graphics.tie.shapes.path._
import k_k_.graphics.tie.shapes.text._
import java.io._
import java.awt.Graphics2D

class ToyRenderer extends Renderer {
  
  /*
   * Public facing render method for Graphics2D
   */
  def render(canvas: Canvas, g2d: Graphics2D): Boolean = do_render(canvas, g2d)
  
  /*
   * This is the primary rendering function
   */
  protected final def do_render(canvas: Canvas, g2d: Graphics2D): Boolean = {
    for { shape <- canvas.shapes } render_shape(shape, g2d)
    true
  }
  
  protected final def render_shape(shape: Shape, g2d: Graphics2D): Unit = {
    shape match {
      case Circle(rad: Double) => g2d.drawOval(0, 0, 3*rad.toInt, 3*rad.toInt)
      case Rectangle (len: Double, wid: Double) => g2d.drawRect(10, 10, len.toInt, wid.toInt)
      //case Elliptical_Arc_Rel (width: double,height: double, startAngle: double, arcAngle: double) => g2d.drawArc(0, 0, width, height, startAngle, arcAngle)
      case _ => Nil
    }
  }
  
  /*============================================================*/
  /* Below are just stubs to satisfy the Renderer trait for now */
  
  protected final def do_render(canvas: Canvas, os: Writer): Boolean = { 
    throw new NotImplementedError("ToyRenderer does not support file IO")
    false
  }
  
  protected final def do_render(canvas: Canvas, os: OutputStream): Boolean = { 
    throw new NotImplementedError("ToyRenderer does not support file IO")
    false
  }
}