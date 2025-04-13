package com.yalcay.camerargb;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class ExcelManager implements AutoCloseable {
    private final String folderPath;
    private Workbook workbook;
    private Sheet rgbSheet;
    private Sheet hsvSheet;
    private int currentRow = 1;  // 0. satır başlıklar için
    private boolean isWorkbookClosed = false;

    private static final List<String> RGB_HEADERS = Arrays.asList(
        "Image Name",
        "Point 1 R", "Point 1 G", "Point 1 B",
        "Point 2 R", "Point 2 G", "Point 2 B",
        "Point 3 R", "Point 3 G", "Point 3 B",
        "Avg R", "Avg G", "Avg B",
        "R", "G", "B",
        "R+G", "R+B", "B+G",
        "R/G", "R/B", "G/B",
        "R/(G+B)", "G/(R+B)", "B/(R+G)",
        "R+G+B",
        "R-G", "R-B", "G-B",
        "R/(G-B)", "G/(R-B)", "B/(R-G)",
        "R-G-B", "G-R-B", "B-G-R",
        "R-G+B", "G-R+B", "B-G+R", "G-B+R"
    );

    private static final List<String> HSV_HEADERS = Arrays.asList(
        "Image Name",
        "Point 1 H", "Point 1 S", "Point 1 V",
        "Point 2 H", "Point 2 S", "Point 2 V",
        "Point 3 H", "Point 3 S", "Point 3 V",
        "Avg H", "Avg S", "Avg V",
        "H", "S", "V",
        "H+S", "H+V", "V+S",
        "H/S", "H/V", "S/V",
        "H/(S+V)", "S/(H+V)", "V/(H+S)",
        "H+S+V",
        "H-S", "H-V", "S-V",
        "H/(S-V)", "S/(H-V)", "V/(H-S)",
        "H-S-V", "S-H-V", "V-S-H",
        "H-S+V", "S-H+V", "V-S+H", "S-V+H"
    );

    public ExcelManager(String folderPath) {
        this.folderPath = folderPath;
        initializeWorkbook();
    }

    private void initializeWorkbook() {
        workbook = new XSSFWorkbook();
        rgbSheet = workbook.createSheet("RGB");
        hsvSheet = workbook.createSheet("HSV");
        
        // Create headers
        createHeaders(rgbSheet, RGB_HEADERS);
        createHeaders(hsvSheet, HSV_HEADERS);
    }

    private void createHeaders(Sheet sheet, List<String> headers) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers.get(i));
        }
    }

    public synchronized void addData(String imageName, ColorProcessor.ColorCalculations calculations) throws IOException {
        if (isWorkbookClosed) {
            initializeWorkbook(); // Yeni bir workbook oluştur
            isWorkbookClosed = false;
        }

        // Add RGB data
        Row rgbRow = rgbSheet.createRow(currentRow);
        Object[] rgbData = calculations.getRGBRowData(imageName);
        for (int i = 0; i < rgbData.length; i++) {
            Cell cell = rgbRow.createCell(i);
            if (rgbData[i] instanceof String) {
                cell.setCellValue((String) rgbData[i]);
            } else if (rgbData[i] instanceof Number) {
                cell.setCellValue(((Number) rgbData[i]).doubleValue());
            }
        }

        // Add HSV data
        Row hsvRow = hsvSheet.createRow(currentRow);
        Object[] hsvData = calculations.getHSVRowData(imageName);
        for (int i = 0; i < hsvData.length; i++) {
            Cell cell = hsvRow.createCell(i);
            if (hsvData[i] instanceof String) {
                cell.setCellValue((String) hsvData[i]);
            } else if (hsvData[i] instanceof Number) {
                cell.setCellValue(((Number) hsvData[i]).doubleValue());
            }
        }

        currentRow++;
        saveWorkbook(); // Her veri eklendiğinde otomatik kaydet
    }

    public synchronized void saveWorkbook() throws IOException {
        if (isWorkbookClosed) {
            throw new IOException("Workbook is already closed");
        }

        // Ensure directory exists
        File folder = new File(folderPath);
        if (!folder.exists()) {
            if (!folder.mkdirs()) {
                throw new IOException("Could not create directory: " + folderPath);
            }
        }

        // Create Excel file
        File file = new File(folder, "color_analysis.xlsx");
        
        FileOutputStream fileOut = null;
        try {
            fileOut = new FileOutputStream(file);
            workbook.write(fileOut);
            fileOut.flush();
        } catch (IOException e) {
            throw new IOException("Error saving Excel file: " + file.getAbsolutePath() + "\n" + e.getMessage());
        } finally {
            if (fileOut != null) {
                try {
                    fileOut.close();
                } catch (IOException e) {
                    // log or handle the exception
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (!isWorkbookClosed) {
            try {
                saveWorkbook();
                if (workbook != null) {
                    workbook.close();
                }
            } finally {
                isWorkbookClosed = true;
            }
        }
    }
}