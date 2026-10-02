package com.pao.reports.samples;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;

/** Initial outer-window placement only. No permanent resizing limits. */
final class PreviewWindowPlacement {
    record Bounds(double x,double y,double width,double height) {}

    static Bounds clamp(Bounds requested,Rectangle2D work) {
        if (work.getWidth()<=0 || work.getHeight()<=0) return requested;
        double width=Math.min(work.getWidth(),positive(requested.width(),work.getWidth()));
        double height=Math.min(work.getHeight(),positive(requested.height(),work.getHeight()));
        double x=Double.isFinite(requested.x())?requested.x():work.getMinX()+(work.getWidth()-width)/2;
        double y=Double.isFinite(requested.y())?requested.y():work.getMinY()+(work.getHeight()-height)/2;
        return new Bounds(Math.max(work.getMinX(),Math.min(x,work.getMaxX()-width)),
            Math.max(work.getMinY(),Math.min(y,work.getMaxY()-height)),width,height);
    }

    private static double positive(double value,double fallback) {
        return Double.isFinite(value)&&value>0?value:fallback;
    }

    static void fitInitially(Stage stage) {
        var requested=new Bounds(stage.getX(),stage.getY(),stage.getWidth(),stage.getHeight());
        var screens=Screen.getScreensForRectangle(Double.isFinite(requested.x())?requested.x():0,
            Double.isFinite(requested.y())?requested.y():0,positive(requested.width(),1),positive(requested.height(),1));
        Screen selected=Screen.getPrimary(); double largest=-1;
        for (var screen:screens) {
            var area=screen.getVisualBounds();
            double overlap=Math.max(0,Math.min(requested.x()+requested.width(),area.getMaxX())-Math.max(requested.x(),area.getMinX()))
                *Math.max(0,Math.min(requested.y()+requested.height(),area.getMaxY())-Math.max(requested.y(),area.getMinY()));
            if(overlap>largest){selected=screen;largest=overlap;}
        }
        var placed=clamp(requested,selected.getVisualBounds());
        stage.setWidth(placed.width());stage.setHeight(placed.height());
        stage.setX(placed.x());stage.setY(placed.y());
    }
}
