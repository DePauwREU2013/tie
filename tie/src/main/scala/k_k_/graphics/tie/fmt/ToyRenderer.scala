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
import java.awt.geom._
import java.awt.geom.AffineTransform

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
    
    def write_shape(s:Shape, transforms: List[AffineTransform]): Unit = {
      
	  s match {
	    // first unwrap all transformations
	    case Translated_Shape(orig: Shape, dx: Double, dy: Double) => {
	      write_shape(orig, AffineTransform.getTranslateInstance(dx, dy) :: transforms)
	    }
	    case Scaled_Shape(orig: Shape, sx: Double, sy: Double) => {
	      write_shape(orig, AffineTransform.getScaleInstance(sx, sy) :: transforms)
	    }
	    case Rotated_Shape(orig, degrees, x_pivot, y_pivot) => {
	      write_shape(orig, AffineTransform.getRotateInstance(math.Pi*degrees/180.0, x_pivot, y_pivot) :: transforms)
	    }
	    /* TODO: need to implement Reflected_Shape transform
	     *   cf: http://en.wikipedia.org/wiki/Transformation_matrix#Reflection
	     */
	    case Skewed_Horiz_Shape(orig, shx) => {
	      // TODO: check to see if shx is actually in the same units
	      write_shape(orig, AffineTransform.getShearInstance(shx, 0) :: transforms)
	    }
	    case Skewed_Vert_Shape(orig, shy) => {
	      // TODO: check to see if shy is actually in the same units
	      write_shape(orig, AffineTransform.getShearInstance(0, shy) :: transforms)
	    }
	    // once all the transformations are extracted
	    // draw an actual shape
	    case transformed_shape if !transforms.isEmpty => {
	      write_transformed_shape(transformed_shape, transforms)
	    }
	    // if there weren't any transforms we can just draw it directly
	    case non_transformed_shape: Shape => {
	      g2d.draw(get_graphics2d_shape(non_transformed_shape))
	    }
	  }
	}
    
    def write_transformed_shape(s: Shape, transforms: List[AffineTransform]): Unit = {
      
      g2d.draw(
          (transforms.foldRight(new AffineTransform())(
            (consT, t) => {
              consT.concatenate(t);
              consT
            })
          ).createTransformedShape(get_graphics2d_shape(s))
      )
    }
    
    def get_graphics2d_shape(s: Shape): java.awt.Shape = {
      s match {
        /*
         * TODO: go through tie.shapes and tie.paths and pair them off with
         *       matching primitives in java.awt.geom.*
         */
        
        case Circle(rad: Double) => new Ellipse2D.Double(0,0,2*rad, 2*rad)
        case Rectangle (h: Double, w: Double) => new Rectangle2D.Double(0,0,h,w)
        
        // java.awt.geom.Area was the most generic "empty" Shape instance I could find
        case _ => new Area()
      }
    }
    
    write_shape(shape, Nil)
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