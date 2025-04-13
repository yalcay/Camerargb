package com.yalcay.camerargb;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import java.util.concurrent.ExecutionException;

public class RGBToConcentrationActivity extends AppCompatActivity {
    private EditText slopeInput;
    private EditText interceptInput;
    private EditText functionInput;
    private TextView resultText;
    private Spinner colorModeSpinner;
    private LinearLayout colorButtonContainer;
    private LinearLayout functionButtonContainer;
    private LinearLayout operatorButtonContainer;
    private ToggleButton[] colorButtons;
    private PreviewView previewView;
    private ImageView rectangleView;
    private Button calculateButton;
    private ImageButton clearFunctionButton;
    private String selectedColorComponent = "";
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rgb_to_concentration);

        initializeViews();
        setupSpinner();
        setupFunctionButtons();
        setupOperatorButtons();
        setupCalculateButton();
        setupClearFunctionButton();
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
        clearFunctionButton = findViewById(R.id.clearFunctionButton);
        colorModeSpinner = findViewById(R.id.colorModeSpinner);

        setupRectangleView();
    }

    private void setupRectangleView() {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setStroke(4, Color.GREEN);
        shape.setColor(Color.TRANSPARENT);

        // 0.5 x 1 cm boyutlarını piksel cinsine çevir
        float density = getResources().getDisplayMetrics().density;
        int width = (int) (0.5 * 37.8 * density);  // 0.5 cm
        int height = (int) (1.0 * 37.8 * density); // 1 cm

        FrameLayout.LayoutParams rectParams = new FrameLayout.LayoutParams(width, height);
        rectParams.gravity = android.view.Gravity.CENTER;
        rectangleView.setLayoutParams(rectParams);
        rectangleView.setBackground(shape);
    }

    private void setupCalculateButton() {
        calculateButton.setOnClickListener(v -> {
            if (selectedColorComponent.isEmpty()) {
                Toast.makeText(this, "Please select a color component", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                double slope = Double.parseDouble(slopeInput.getText().toString());
                double intercept = Double.parseDouble(interceptInput.getText().toString());

                // Get color value from the center of rectangle area
                int[] location = new int[2];
                rectangleView.getLocationInWindow(location);
                int centerX = location[0] + rectangleView.getWidth() / 2;
                int centerY = location[1] + rectangleView.getHeight() / 2;

                // Get the color value
                double colorValue = getColorValue(selectedColorComponent, centerX, centerY);

                // Calculate concentration using the formula: concentration = (colorValue - intercept) / slope
                double concentration = (colorValue - intercept) / slope;

                // Display result
                resultText.setText(String.format("Color Value: %.2f\nConcentration: %.2f", 
                    colorValue, concentration));

            } catch (NumberFormatException e) {
                Toast.makeText(this, "Please enter valid slope and intercept values", 
                    Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item,
            new String[]{"RGB", "HSV"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        colorModeSpinner.setAdapter(adapter);
        
        colorModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                setupColorButtons(position == 0 ? "RGB" : "HSV");
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupColorButtons(String mode) {
        colorButtonContainer.removeAllViews();
        String[] colors = mode.equals("RGB") ? new String[]{"R", "G", "B"} : new String[]{"H", "S", "V"};
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
                    updateFunctionInput(color);
                }
            });

            colorButtonContainer.addView(button);
            colorButtons[i] = button;
        }
    }

    private void setupFunctionButtons() {
        String[] functions = {"x", "√x", "x²", "1/x"};
        functionButtonContainer.removeAllViews();

        for (String function : functions) {
            Button button = new Button(this);
            button.setText(function);
            button.setOnClickListener(v -> appendToFunction(function));
            functionButtonContainer.addView(button);
        }
    }

    private void setupOperatorButtons() {
        String[] operators = {"+", "-", "×", "÷"};
        operatorButtonContainer.removeAllViews();

        for (String operator : operators) {
            Button button = new Button(this);
            button.setText(operator);
            button.setOnClickListener(v -> appendToFunction(" " + operator + " "));
            operatorButtonContainer.addView(button);
        }
    }

    private void setupClearFunctionButton() {
        clearFunctionButton.setOnClickListener(v -> functionInput.setText(""));
    }

    private void appendToFunction(String text) {
        String currentText = functionInput.getText().toString();
        text = text.replace("x", selectedColorComponent.isEmpty() ? "x" : selectedColorComponent);
        functionInput.setText(currentText + text);
    }

    private void updateFunctionInput(String newComponent) {
        String currentFunction = functionInput.getText().toString();
        for (String color : new String[]{"R", "G", "B", "H", "S", "V"}) {
            currentFunction = currentFunction.replace(color, newComponent);
        }
        functionInput.setText(currentFunction);
    }

	private void calculateConcentration() {
		if (selectedColorComponent.isEmpty()) {
			Toast.makeText(this, "Please select a color component", Toast.LENGTH_SHORT).show();
			return;
		}

		try {
			double slope = Double.parseDouble(slopeInput.getText().toString());
			double intercept = Double.parseDouble(interceptInput.getText().toString());

			// Dikdörtgenin merkezinden renk değerini al
			int[] location = new int[2];
			rectangleView.getLocationInWindow(location);
			int centerX = location[0] + rectangleView.getWidth() / 2;
			int centerY = location[1] + rectangleView.getHeight() / 2;

			// Renk değerini al
			double colorValue = getColorValue(selectedColorComponent, centerX, centerY);

			// Fonksiyonu uygula
			String function = functionInput.getText().toString();
			double transformedValue = applyFunction(colorValue, function);

			// Konsantrasyonu hesapla: (transformedValue - intercept) / slope
			double concentration = (transformedValue - intercept) / slope;

			// Sonucu göster
			resultText.setText(String.format(
				"Color Value: %.2f\nTransformed Value: %.2f\nConcentration: %.2f",
				colorValue, transformedValue, concentration));

		} catch (NumberFormatException e) {
			Toast.makeText(this, "Please enter valid slope and intercept values", 
				Toast.LENGTH_SHORT).show();
		} catch (Exception e) {
			Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
		}
	}

	private double applyFunction(double value, String function) {
		if (function.contains("1/")) {
			return 1.0 / value;
		} else if (function.contains("√")) {
			return Math.sqrt(value);
		} else if (function.contains("²")) {
			return value * value;
		} else if (function.contains("+")) {
			String[] parts = function.split("\\+");
			double sum = 0;
			for (String part : parts) {
				sum += evaluateExpression(part.trim(), value);
			}
			return sum;
		} else if (function.contains("-")) {
			String[] parts = function.split("-");
			double result = evaluateExpression(parts[0].trim(), value);
			for (int i = 1; i < parts.length; i++) {
				result -= evaluateExpression(parts[i].trim(), value);
			}
			return result;
		} else if (function.contains("×")) {
			String[] parts = function.split("×");
			double product = 1;
			for (String part : parts) {
				product *= evaluateExpression(part.trim(), value);
			}
			return product;
		} else if (function.contains("÷")) {
			String[] parts = function.split("÷");
			double result = evaluateExpression(parts[0].trim(), value);
			for (int i = 1; i < parts.length; i++) {
				double divisor = evaluateExpression(parts[i].trim(), value);
				if (divisor == 0) {
					throw new ArithmeticException("Division by zero");
				}
				result /= divisor;
			}
			return result;
		}
		
		return value; // Fonksiyon yoksa değeri aynen döndür
	}

	private double evaluateExpression(String expression, double value) {
		expression = expression.trim();
		if (expression.isEmpty()) return 0;
		
		if (expression.contains("1/")) {
			return 1.0 / value;
		} else if (expression.contains("√")) {
			return Math.sqrt(value);
		} else if (expression.contains("²")) {
			return value * value;
		} else {
			try {
				return Double.parseDouble(expression);
			} catch (NumberFormatException e) {
				return value;
			}
		}
	}

    private double getColorValue(String component, int x, int y) {
        // Get bitmap from preview
        Bitmap bitmap = previewView.getBitmap();
        if (bitmap == null) return 0;

        // Get color from center point
        int pixel = bitmap.getPixel(
            Math.min(Math.max(x, 0), bitmap.getWidth() - 1),
            Math.min(Math.max(y, 0), bitmap.getHeight() - 1)
        );

        switch (component) {
            case "R": return Color.red(pixel);
            case "G": return Color.green(pixel);
            case "B": return Color.blue(pixel);
            case "H":
            case "S":
            case "V":
                float[] hsv = new float[3];
                Color.RGBToHSV(Color.red(pixel), Color.green(pixel), Color.blue(pixel), hsv);
                return component.equals("H") ? hsv[0] : 
                       component.equals("S") ? hsv[1] * 100 : 
                       hsv[2] * 100;
            default:
                return 0;
        }
    }

    // Camera setup metodları aynı kalacak
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