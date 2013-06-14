/*
   file: k_k_/test/graphics/tie/Graphics2D_Render_Test.scala

   Tie - Tie Illustrates Everything.

     http://www.tie-illustrates-everything.com/

   Copyright (c)2013 by Merv Fansler
   All Rights Reserved.

   Please reference the following for applicable terms, conditions,
   and licensing:

     http://tie-illustrates-everything.com/licensing
*/

package k_k_.test.graphics.tie

import org.junit._
import Assert._
import org.scalatest.junit.JUnitSuite
import k_k_.graphics.tie._
import k_k_.graphics.tie.Attribution._
import k_k_.graphics.tie.ink.{Named_Colors => C, _}
import k_k_.graphics.tie.shapes._
import java.awt.{Graphics, Graphics2D, Color}
import javax.swing._
//import java.awt._
//import java.awt.Color
import k_k_.graphics.tie.Canvas
import k_k_.graphics.tie.fmt.ToyRenderer
import k_k_.graphics.tie.fmt.graphics2d.Graphics2D_Renderer
//import java.awt.event.WindowStateListener
//import java.awt.event.WindowAdapter
//import java.awt.event.WindowEvent
//import scala.swing.Dialog

@Test
class Graphics2D_Render_Test extends JUnitSuite {

  class CanvasPanel(canvas: Canvas) extends JPanel {
    
    // for now we'll just use the toy renderer
    val renderer: ToyRenderer = new ToyRenderer();
    
    override def paintComponent(g: Graphics) = synchronized {
      super.paintComponent(g)
      
      val g2d = g.asInstanceOf[Graphics2D]
      renderer.render(canvas, g2d)
    }
  }
  
  @Test
  def test_Graphics2D() = {
          
	  val frame = new JFrame("Graphics Test")
	  frame.setSize(600, 400)
	  
	  /*
	   * This should be keeping the window, but JUnit still kills it immediately :(
	   */
//	  frame.addWindowListener(new WindowAdapter() {
//	    
//	    override def windowClosing(event: WindowEvent): Unit = {
//	      Dialog.showConfirmation(parent = null,
//	    		  title = "Exit",
//	    		  message = "Are you sure you want to quit?"
//	      ) match {
//	      	case Dialog.Result.Ok => exit(0)
//	      	case _ => ()
//	      }
//	    }
//	  })
	  
	  /*
	   * NOTE: I'm temporarily using the syntax
	   * 
	   * function(param
	   * 		  param
	   *     //   , param
	   *        , param
	   *        )
	   * 
	   * because its easier to comment out
	   * while we change things in this file 
	   * a lot. When things are under less 
	   * flux, we can switch to a more
	   * common form.
	   */
	  
	  val canvas: Canvas = new Canvas(
	      Canvas_Props(600, 400, Origin_Top_Left, "Test Output"),
	      Circle(100) -* 2
	    , Rectangle(50,70) -% 10 -+ (100, 100)
	    // see: not commutative
	    , Square(60) -/- 45 -/| 45
	    , Square(50) -/| 45 -/- 45
	    )
	  
	  val panel = new CanvasPanel(canvas)
	  panel.setBackground(Color.WHITE)
	  panel.setSize(600,400)
	  
	  frame.setContentPane(panel)
	  frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE)
	  frame.setVisible(true)
	  
  }
  
}