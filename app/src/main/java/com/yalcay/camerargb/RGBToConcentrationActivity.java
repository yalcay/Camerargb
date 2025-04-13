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
    private GridLayout buttonGrid;
    private Button[] colorButtons;
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
        setupColorButtons();
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
        colorModeSpinner = findViewById(R.id.colorModeSpinner);
        buttonGrid = findViewById(R.id.buttonGrid);
        previewView = findViewById(R.id.previewView);
        rectangleView = findViewById(R.id.rectangleView);
        calculateButton = findViewById(R.id.calculateButton);
        clearFunctionButton = findViewById(R.id.clearFunctionButton);

        colorButtons = new Button[3];
        colorButtons[0] = findViewById(R.id.colorButton1);
        colorButtons[1] = findViewById(R.id.colorButton2);
        colorButtons[2] = findViewById(R.id.colorButton3);

        setupRectangleView();
    }

    private void setupRectangleView() {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setStroke(4, Color.GREEN);
        shape.setColor(Color.TRANSPARENT);

        float density = getResources().getDisplayMetrics().density;
        int width = (int) (0.5 * 37.8 * density);  // 0.5 cm
        int height = (int) (1.0 * 37.8 * density); // 1 cm

        FrameLayout.LayoutParams rectParams = new FrameLayout.LayoutParams(width, height);
        rectParams.gravity = android.view.Gravity.CENTER;
        rectangleView.setLayoutParams(rectParams);
        rectangleView.setBackground(shape);
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
                boolean isRGB = position == 0;
                colorButtons[0].setText(isRGB ? "R" : "H");
                colorButtons[1].setText(isRGB ? "G" : "S");
                colorButtons[2].setText(isRGB ? "B" : "V");
                selectedColorComponent = ""; // Reset selection
                
                // Reset button states
                for (Button button : colorButtons) {
                    button.setSelected(false);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupColorButtons() {
        for (Button button : colorButtons) {
            button.setOnClickListener(v -> {
                String component = ((Button) v).getText().toString();
                selectedColorComponent = component;
                updateFunctionInput(component);
                
                // Toggle button states
                for (Button other : colorButtons) {
                    other.setSelected(other == v);
                }
            });
        }
    }

	private void setupFunctionButtons() {
		Button[] functionButtons = new Button[]{
			findViewById(R.id.functionX),
			findViewById(R.id.functionSqrt),
			findViewById(R.id.functionSquare),
			findViewById(R.id.functionReciprocal)
		};

		String[] functions = {"x", "√x", "x²", "1/x"};
		for (int i = 0; i < functionButtons.length; i++) {
			Button button = functionButtons[i];
			String function = functions[i];
			button.setOnClickListener(v -> appendToFunction(function));
		}
	}

	private void setupOperatorButtons() {
		Button[] operatorButtons = new Button[]{
			findViewById(R.id.operatorAdd),
			findViewById(R.id.operatorSubtract),
			findViewById(R.id.operatorMultiply),
			findViewById(R.id.operatorDivide)
		};

		String[] operators = {"+", "-", "×", "÷"};
		for (int i = 0; i < operatorButtons.length; i++) {
			Button button = operatorButtons[i];
			String operator = operators[i];
			button.setOnClickListener(v -> appendToFunction(" " + operator + " "));
		}
	}

    private void setupCalculateButton() {
        calculateButton.setOnClickListener(v -> calculateConcentration());
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

    // ... (calculateConcentration, applyFunction, evaluateExpression, getColorValue metodları aynı kalacak) ...

    private void calculateConcentration() {
        if (selectedColorComponent.isEmpty()) {
            Toast.makeText(this, "Please select a color component", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            double slope = Double.parseDouble(slopeInput.getText().toString());
            double intercept = Double.parseDouble(interceptInput.getText().toString());

            int[] location = new int[2];
            rectangleView.getLocationInWindow(location);
            int centerX = location[0] + rectangleView.getWidth() / 2;
            int centerY = location[1] + rectangleView.getHeight() / 2;

            double colorValue = getColorValue(selectedColorComponent, centerX, centerY);
            String function = functionInput.getText().toString();
            double transformedValue = applyFunction(colorValue, function);
            double concentration = (transformedValue - intercept) / slope;

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