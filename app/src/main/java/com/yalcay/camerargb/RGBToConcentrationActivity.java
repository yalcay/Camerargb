package com.yalcay.camerargb;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import android.widget.ToggleButton;
import com.google.common.util.concurrent.ListenableFuture;
import android.widget.ImageView;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;
import java.util.concurrent.ExecutionException;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;

public class RGBToConcentrationActivity extends AppCompatActivity {
    private EditText slopeInput;
    private EditText interceptInput;
    private EditText functionInput;
    private TextView resultText;
    private ToggleButton[] colorButtons;
    private Button[] functionButtons;
    private PreviewView previewView;
    private ImageView rectangleView;
    private Button calculateButton;
    private String selectedColorComponent = "";
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rgb_to_concentration);

        initializeViews();
        setupColorButtons();
        setupFunctionButtons();
        setupCalculateButton();
        setupCamera();
    }

    private void initializeViews() {
        slopeInput = findViewById(R.id.slopeInput);
        interceptInput = findViewById(R.id.interceptInput);
        functionInput = findViewById(R.id.functionInput);
        resultText = findViewById(R.id.resultText);
        previewView = findViewById(R.id.previewView);
        rectangleView = findViewById(R.id.rectangleView);
        calculateButton = findViewById(R.id.calculateButton);

        // Rectangle view setup
        setupRectangleView();
    }

	private void setupRectangleView() {
		GradientDrawable shape = new GradientDrawable();
		shape.setShape(GradientDrawable.RECTANGLE);
		shape.setStroke(4, Color.GREEN);
		shape.setColor(Color.TRANSPARENT);

		// Piksel cinsinden boyutlar
		int width = 150;  // 5mm yaklaşık
		int height = 300; // 10mm yaklaşık

		FrameLayout.LayoutParams rectParams = new FrameLayout.LayoutParams(width, height);
		rectParams.gravity = android.view.Gravity.CENTER;
		rectangleView.setLayoutParams(rectParams);
		rectangleView.setBackground(shape);
	}

    private void setupColorButtons() {
        String[] colors = {"R", "G", "B", "H", "S", "V"};
        LinearLayout buttonContainer = findViewById(R.id.colorButtonContainer);
        colorButtons = new ToggleButton[colors.length];

        for (int i = 0; i < colors.length; i++) {
            ToggleButton button = new ToggleButton(this);
            button.setTextOn(colors[i]);
            button.setTextOff(colors[i]);
            button.setText(colors[i]);
            
            final String color = colors[i];
            button.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedColorComponent = color;
                    for (ToggleButton otherButton : colorButtons) {
                        if (otherButton != buttonView) {
                            otherButton.setChecked(false);
                        }
                    }
                    updateFunctionInput();
                }
            });

            buttonContainer.addView(button);
            colorButtons[i] = button;
        }
    }

    private void setupFunctionButtons() {
        String[] functions = {"x", "√x", "x²", "1/x"};
        LinearLayout buttonContainer = findViewById(R.id.functionButtonContainer);
        functionButtons = new Button[functions.length];

        for (int i = 0; i < functions.length; i++) {
            Button button = new Button(this);
            button.setText(functions[i]);
            
            final String function = functions[i];
            button.setOnClickListener(v -> {
                String currentText = functionInput.getText().toString();
                String newText = currentText + function.replace("x", selectedColorComponent);
                functionInput.setText(newText);
            });

            buttonContainer.addView(button);
            functionButtons[i] = button;
        }
    }

    private void updateFunctionInput() {
        String currentFunction = functionInput.getText().toString();
        for (String color : new String[]{"R", "G", "B", "H", "S", "V"}) {
            currentFunction = currentFunction.replace(color, selectedColorComponent);
        }
        functionInput.setText(currentFunction);
    }

    private void setupCalculateButton() {
        calculateButton.setOnClickListener(v -> calculateConcentration());
    }

    private void calculateConcentration() {
        if (selectedColorComponent.isEmpty()) {
            Toast.makeText(this, "Please select a color component", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            double slope = Double.parseDouble(slopeInput.getText().toString());
            double intercept = Double.parseDouble(interceptInput.getText().toString());

            // Get color value from rectangle area
            int[] location = new int[2];
            rectangleView.getLocationInWindow(location);
            int rectWidth = rectangleView.getWidth();
            int rectHeight = rectangleView.getHeight();

            // Capture the current frame
            ImageCapture imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

            // Get the color value
            double colorValue = getColorValue(selectedColorComponent, location[0], location[1], 
                rectWidth, rectHeight);

            // Apply function transformation
            double transformedValue = applyFunction(colorValue);

            // Calculate concentration
            double concentration = (transformedValue * slope) + intercept;

            // Display result
            resultText.setText(String.format("Concentration: %.2f", concentration));

        } catch (NumberFormatException e) {
            Toast.makeText(this, "Please enter valid slope and intercept values", 
                Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private double getColorValue(String component, int x, int y, int width, int height) {
        // Get bitmap from preview
        Bitmap bitmap = previewView.getBitmap();
        if (bitmap == null) return 0;

        // Calculate average color in rectangle area
        int totalPixels = 0;
        double sum = 0;

        for (int i = x; i < x + width && i < bitmap.getWidth(); i++) {
            for (int j = y; j < y + height && j < bitmap.getHeight(); j++) {
                int pixel = bitmap.getPixel(i, j);
                double value = 0;

                switch (component) {
                    case "R": value = Color.red(pixel); break;
                    case "G": value = Color.green(pixel); break;
                    case "B": value = Color.blue(pixel); break;
                    case "H":
                    case "S":
                    case "V":
                        float[] hsv = new float[3];
                        Color.RGBToHSV(Color.red(pixel), Color.green(pixel), Color.blue(pixel), hsv);
                        value = component.equals("H") ? hsv[0] : 
                               component.equals("S") ? hsv[1] * 100 : 
                               hsv[2] * 100;
                        break;
                }

                sum += value;
                totalPixels++;
            }
        }

        return totalPixels > 0 ? sum / totalPixels : 0;
    }

    private double applyFunction(double value) {
        String function = functionInput.getText().toString();
        
        if (function.contains("²")) {
            return value * value;
        } else if (function.contains("√")) {
            return Math.sqrt(value);
        } else if (function.contains("1/")) {
            return 1.0 / value;
        }
        
        return value; // linear case
    }

    private void setupCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, "Error starting camera: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        CameraSelector cameraSelector = new CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build();

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, cameraSelector, preview);
        } catch (Exception e) {
            Toast.makeText(this, "Error binding camera: " + e.getMessage(),
                Toast.LENGTH_SHORT).show();
        }
    }
}