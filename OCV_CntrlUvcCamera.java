import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JButton;
import javax.swing.JDialog;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;

/*
 * The MIT License
 *
 * Copyright 2016 Takehito Nishida.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

/**
 * Control UVC camera using VideoCapture function (OpenCV3.3.1).
 */
public class OCV_CntrlUvcCamera implements ExtendedPlugInFilter
{
    // const var.
    private final int FLAGS = NO_IMAGE_REQUIRED;
    // from /sources/modules/videoio/include/opencv2/videoio/videoio_c.h
    private final int CV_CAP_PROP_FRAME_WIDTH = 3;
    private final int CV_CAP_PROP_FRAME_HEIGHT = 4;

    // static var.
    private static int device = 0;
    private static int width = 640;
    private static int height = 480;
    private static int wait_time = 100;

    // var.
    private String title = null;
    public JDialog diag_free = null;
    private boolean flag_fin_loop = false;

      @Override
    public int showDialog(ImagePlus arg0, String cmd, PlugInFilterRunner arg2)
    {
        title = cmd.trim();
        GenericDialog gd = new GenericDialog(title + "...");

        gd.addNumericField("device", device, 0);
        gd.addNumericField("width", width, 0);
        gd.addNumericField("height", height, 0);
        gd.addNumericField("wait_time", wait_time, 0);

        gd.showDialog();

        if (gd.wasCanceled())
        {
            return DONE;
        }
        else
        {
            device = (int)gd.getNextNumber();
            width = (int)gd.getNextNumber();
            height = (int)gd.getNextNumber();
            wait_time = (int)gd.getNextNumber();

            return FLAGS;
        }
    }

    @Override
    public void setNPasses(int arg0)
    {
        // do nothing
    }

    @Override
    public int setup(String arg0, ImagePlus arg1)
    {
        if(!OCV__LoadLibrary.isLoad())
        {
            IJ.error("Library is not loaded.");
            return DONE;
        }

        return FLAGS;
    }

    @Override
    public void run(ImageProcessor arg0)
    {
        boolean bret = true;

        // ----- stop dialog during continuous grabbing -----
        diag_free = new JDialog(diag_free, title, false);
        JButton but_stop_cont = new JButton("Stop");

        but_stop_cont.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                flag_fin_loop = true;
                diag_free.dispose();
            }
        });

        diag_free.addWindowListener(new WindowAdapter() {
              @Override
              public void windowClosing(WindowEvent e) {
                  flag_fin_loop = true;
              }
        });

        diag_free.add(but_stop_cont);
        diag_free.setSize(100, 75);
        // ----- end of stop dialog -----

        // initialize camera
        VideoCapture src_cap =new VideoCapture();
        Mat src_mat = new Mat();
        bret = src_cap.open(device);

        if(!bret)
        {
            IJ.error("Camera initialization is failed.");
            diag_free.dispose();
            return;
        }

        src_cap.set(CV_CAP_PROP_FRAME_WIDTH, width);
        src_cap.set(CV_CAP_PROP_FRAME_HEIGHT, height);

        // Setting the image display window
        width = (int) src_cap.get(CV_CAP_PROP_FRAME_WIDTH);
        height = (int) src_cap.get(CV_CAP_PROP_FRAME_HEIGHT);

        ImagePlus  imp_dsp = IJ.createImage(title, width, height, 1, 24);
        int[] impdsp_intarray = (int[])imp_dsp.getChannelProcessor().getPixels();
        imp_dsp.show();
        
        // show stop dialog
        diag_free.setVisible(true);

        // run
        for(;;)
        {
            if(flag_fin_loop)
            {
                break;
            }

            // grab
            imp_dsp.startTiming();
            bret = src_cap.read(src_mat);
            IJ.showTime(imp_dsp, imp_dsp.getStartTime(), title + " : ");
            
            if(!bret)
            {
                IJ.error("Error occurred in grabbing.");
                diag_free.dispose();
                break;
            }

            if(src_mat.empty())
            {
                IJ.error("Mat is empty.");
                diag_free.dispose();
                break;
            }
            
            // display
            if(!imp_dsp.isVisible())
            {
                imp_dsp = null;
                imp_dsp = IJ.createImage(title, width, height, 1, 24);
                impdsp_intarray = (int[])imp_dsp.getChannelProcessor().getPixels();
                imp_dsp.show();
            }
            
            if(src_mat.type() == CvType.CV_8UC3)
            {
                OCV__LoadLibrary.mat2intarray(src_mat, impdsp_intarray, width, height);
            }
            else
            {
                IJ.error("Color camera is supported only.");
                diag_free.dispose();
                break;
            }

            imp_dsp.draw();

            // wait
            OCV__LoadLibrary.Wait(wait_time);
        }

        diag_free.dispose();
        
        if(src_cap.isOpened())
        {
            src_cap.release();
        }
    }
}
