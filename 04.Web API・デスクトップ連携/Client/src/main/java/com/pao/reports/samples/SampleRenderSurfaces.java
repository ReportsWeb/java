package com.pao.reports.samples;

import com.pao.reports.engine.render.*;
import java.awt.image.BufferedImage;

/** The single pre-presentation raster contract shared by preview and printing. */
final class SampleRenderSurfaces {
    private SampleRenderSurfaces() {}
    static BufferedImage previewInput(SampleCatalog.SamplePage page,double dpi,RenderControl control){return canonical(page,dpi,control);}
    static BufferedImage printableInput(SampleCatalog.SamplePage page,double dpi,RenderControl control){return canonical(page,dpi,control);}
    private static BufferedImage canonical(SampleCatalog.SamplePage page,double dpi,RenderControl control){return new ReportRenderer().renderPage(page.definition(),page.data(),new RenderOptions(dpi,false,page.sourcePage()),page.sourcePage(),control==null?RenderControl.none():control);}
}
