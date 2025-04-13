package com.yalcay.camerargb;

import android.graphics.Bitmap;
import android.graphics.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import java.util.function.ToDoubleFunction;

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
    
    public static List<ColorPoint> processRectangleArea(Bitmap bitmap, int startX, int startY, 
                                                      int width, int height) {
        List<ColorPoint> points = new ArrayList<>();
        
        // Dikdörtgeni 3 eşit parçaya böl
        int partHeight = height / 3;
        
        // Her bölümün ortasından bir nokta al
        for (int i = 0; i < 3; i++) {
            int y = startY + (i * partHeight) + (partHeight / 2);
            int x = startX + (width / 2);
            
            // Koordinatların bitmap sınırları içinde olduğundan emin ol
            x = Math.min(Math.max(x, 0), bitmap.getWidth() - 1);
            y = Math.min(Math.max(y, 0), bitmap.getHeight() - 1);
            
            int pixel = bitmap.getPixel(x, y);
            points.add(new ColorPoint(pixel));
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
            // RGB değerleri için ortalama hesapla
            double avgR = avg(p -> p.r);
            double avgG = avg(p -> p.g);
            double avgB = avg(p -> p.b);
            
            return new Object[] {
                imageName,
                // Point 1
                points.get(0).r, points.get(0).g, points.get(0).b,
                // Point 2
                points.get(1).r, points.get(1).g, points.get(1).b,
                // Point 3
                points.get(2).r, points.get(2).g, points.get(2).b,
                // Averages
                avgR, avgG, avgB,
                // Raw values
                avgR, avgG, avgB,
                // Combinations
                avgR + avgG, avgR + avgB, avgB + avgG,
                // Ratios
                avgR / avgG, avgR / avgB, avgG / avgB,
                // Complex ratios
                avgR / (avgG + avgB), avgG / (avgR + avgB), avgB / (avgR + avgG),
                // Sum
                avgR + avgG + avgB,
                // Differences
                avgR - avgG, avgR - avgB, avgG - avgB,
                // Complex ratios with differences
                avgR / Math.max(0.1, (avgG - avgB)), 
                avgG / Math.max(0.1, (avgR - avgB)), 
                avgB / Math.max(0.1, (avgR - avgG)),
                // Multiple differences
                avgR - avgG - avgB, avgG - avgR - avgB, avgB - avgG - avgR,
                // Complex combinations
                avgR - avgG + avgB, avgG - avgR + avgB, avgB - avgG + avgR, avgG - avgB + avgR
            };
        }
        
        public Object[] getHSVRowData(String imageName) {
            // HSV değerleri için ortalama hesapla
            double avgH = avgFloat(p -> p.h);
            double avgS = avgFloat(p -> p.s);
            double avgV = avgFloat(p -> p.v);
            
            return new Object[] {
                imageName,
                // Point 1
                points.get(0).h, points.get(0).s, points.get(0).v,
                // Point 2
                points.get(1).h, points.get(1).s, points.get(1).v,
                // Point 3
                points.get(2).h, points.get(2).s, points.get(2).v,
                // Averages
                avgH, avgS, avgV,
                // Raw values
                avgH, avgS, avgV,
                // Combinations
                avgH + avgS, avgH + avgV, avgV + avgS,
                // Ratios
                avgH / Math.max(0.1, avgS), 
                avgH / Math.max(0.1, avgV), 
                avgS / Math.max(0.1, avgV),
                // Complex ratios
                avgH / Math.max(0.1, (avgS + avgV)), 
                avgS / Math.max(0.1, (avgH + avgV)), 
                avgV / Math.max(0.1, (avgH + avgS)),
                // Sum
                avgH + avgS + avgV,
                // Differences
                avgH - avgS, avgH - avgV, avgS - avgV,
                // Complex ratios with differences
                avgH / Math.max(0.1, (avgS - avgV)), 
                avgS / Math.max(0.1, (avgH - avgV)), 
                avgV / Math.max(0.1, (avgH - avgS)),
                // Multiple differences
                avgH - avgS - avgV, avgS - avgH - avgV, avgV - avgS - avgH,
                // Complex combinations
                avgH - avgS + avgV, avgS - avgH + avgV, avgV - avgS + avgH, avgS - avgV + avgH
            };
        }
    }
}