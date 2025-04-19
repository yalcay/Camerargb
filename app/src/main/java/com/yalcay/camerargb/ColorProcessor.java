package com.yalcay.camerargb;

import android.graphics.Bitmap;
import android.graphics.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import java.util.function.ToDoubleFunction;
import java.util.Arrays;

public class ColorProcessor {
    public static class ColorPoint {
        public final int r, g, b;
        public final float h, s, v;
        
        public ColorPoint(int color) {
            r = Color.red(color);
            g = Color.green(color);
            b = Color.blue(color);
            
            float[] hsv = new float[3];
            Color.RGBToHSV(r, g, b, hsv);
            h = hsv[0];
            s = hsv[1];
            v = hsv[2];
        }
    }
    
    public static List<ColorPoint> processRectangleArea(Bitmap bitmap, int startX, int startY, int width, int height) {
        List<ColorPoint> points = new ArrayList<>();
        int partHeight = height / 3;
        for (int i = 0; i < 3; i++) {
            int y = startY + (i * partHeight) + (partHeight / 2);
            int x = startX + (width / 2);
            x = Math.min(Math.max(x, 0), bitmap.getWidth() - 1);
            y = Math.min(Math.max(y, 0), bitmap.getHeight() - 1);
            int pixel = bitmap.getPixel(x, y);
            points.add(new ColorPoint(pixel));
        }
        System.out.println("Extracted " + points.size() + " points from bitmap.");
        for (ColorPoint point : points) {
            System.out.println("RGB: (" + point.r + ", " + point.g + ", " + point.b + ") - " +
                               "HSV: (" + point.h + ", " + point.s + ", " + point.v + ")");
        }
        return points;
    }
    
    public static class ColorCalculations {
        private final List<ColorPoint> points;
        
        public ColorCalculations(List<ColorPoint> points) {
            this.points = points;
        }
        
        private double avg(ToIntFunction<ColorPoint> selector) {
            return points.stream()
                .mapToInt(selector)
                .average()
                .orElse(0.0);
        }
        
        private double avgFloat(ToDoubleFunction<ColorPoint> selector) {
            return points.stream()
                .mapToDouble(selector)
                .average()
                .orElse(0.0);
        }
        
        public Object[] getRGBRowData(String imageName) {
            double avgR = avg(p -> p.r);
            double avgG = avg(p -> p.g);
            double avgB = avg(p -> p.b);
            Object[] data = new Object[] {
                imageName,
                points.get(0).r, points.get(0).g, points.get(0).b,
                points.get(1).r, points.get(1).g, points.get(1).b,
                points.get(2).r, points.get(2).g, points.get(2).b,
                avgR, avgG, avgB,
                avgR, avgG, avgB,
                avgR + avgG, avgR + avgB, avgB + avgG,
                avgR / Math.max(0.1, avgG), avgR / Math.max(0.1, avgB), avgG / Math.max(0.1, avgB),
                avgR / (avgG + avgB), avgG / (avgR + avgB), avgB / (avgR + avgG),
                avgR + avgG + avgB,
                avgR - avgG, avgR - avgB, avgG - avgB,
                avgR / Math.max(0.1, (avgG - avgB)), 
                avgG / Math.max(0.1, (avgR - avgB)), 
                avgB / Math.max(0.1, (avgR - avgG)),
                avgR - avgG - avgB, avgG - avgR - avgB, avgB - avgG - avgR,
                avgR - avgG + avgB, avgG - avgR + avgB, avgB - avgG + avgR, avgG - avgB + avgR
            };
            System.out.println("Calculated RGB row data: " + Arrays.toString(data));
            return data;
        }
        
        public Object[] getHSVRowData(String imageName) {
            double avgH = avgFloat(p -> p.h);
            double avgS = avgFloat(p -> p.s);
            double avgV = avgFloat(p -> p.v);
            Object[] data = new Object[] {
                imageName,
                points.get(0).h, points.get(0).s, points.get(0).v,
                points.get(1).h, points.get(1).s, points.get(1).v,
                points.get(2).h, points.get(2).s, points.get(2).v,
                avgH, avgS, avgV,
                avgH, avgS, avgV,
                avgH + avgS, avgH + avgV, avgV + avgS,
                avgH / Math.max(0.1, avgS), avgH / Math.max(0.1, avgV), avgS / Math.max(0.1, avgV),
                avgH / (avgS + avgV), avgS / (avgH + avgV), avgV / (avgH + avgS),
                avgH + avgS + avgV,
                avgH - avgS, avgH - avgV, avgS - avgV,
                avgH / Math.max(0.1, (avgS - avgV)), avgS / Math.max(0.1, (avgH - avgV)), avgV / Math.max(0.1, (avgH - avgS)),
                avgH - avgS - avgV, avgS - avgH - avgV, avgV - avgS - avgH,
                avgH - avgS + avgV, avgS - avgH + avgV, avgV - avgS + avgH, avgS - avgV + avgH
            };
            System.out.println("Calculated HSV row data: " + Arrays.toString(data));
            return data;
        }
    }
}