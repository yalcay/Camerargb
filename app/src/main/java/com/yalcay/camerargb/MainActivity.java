package com.yalcay.camerargb;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.TimeZone;
import android.view.Gravity;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.SurfaceOrientedMeteringPointFactory;
import java.util.concurrent.TimeUnit;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.view.MotionEvent;

public class MainActivity extends AppCompatActivity {
    private MaterialButton btnNewStudy;
    private MaterialButton btnRgbToConcentration;
    private MaterialButton btnCalibrate;
    private FrameLayout cameraOverlay;
    private ImageView rectangleView;
    private PreviewView previewView;
    private String currentStudyFolder;
    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final int STORAGE_PERMISSION_CODE = 101;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private RecyclerView photoRecyclerView;
    private PhotoAdapter photoAdapter;
    private Button btnFinishStudy;
    private ImageCapture imageCapture;
    private ImageButton captureButton;
    private ExcelManager excelManager;
	private Camera camera;
    private List<PhotoData> pendingPhotos = new ArrayList<>(); // Analiz bekleyen fotoğraflar
    private boolean isProcessingExcel = false; // Excel işlemi devam ediyor mu?

    private static class PhotoData {
        File photoFile;
        Bitmap bitmap;

        PhotoData(File photoFile, Bitmap bitmap) {
            this.photoFile = photoFile;
            this.bitmap = bitmap;
        }
    }
	
	private void setupNewStudyButton() {
		btnNewStudy.setOnClickListener(v -> {
			if (isProcessingExcel) {
				Toast.makeText(this, 
					"Please wait for the current study to finish processing", 
					Toast.LENGTH_LONG).show();
				return;
			}
			showStudyNameDialog();  // Eğer Excel işlemi devam etmiyorsa, yeni çalışma başlat
		});
	}
    private void processPhoto(File photoFile) {
        try {
            Bitmap originalBitmap = BitmapFactory.decodeFile(photoFile.getAbsolutePath());
            // Bitmap'i ve dosyayı listeye ekle
            pendingPhotos.add(new PhotoData(photoFile, originalBitmap));

            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, 
                    "Photo saved successfully", 
                    Toast.LENGTH_SHORT).show();
            });
        } catch (Exception e) {
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, 
                    "Error saving image: " + e.getMessage(), 
                    Toast.LENGTH_LONG).show();
            });
            e.printStackTrace();
        }
    }

	private void showFinishStudyDialog() {
		new AlertDialog.Builder(this)
			.setTitle("Finish Study")
			.setMessage("Do you want to finish this study?")
			.setPositiveButton("Yes", (dialog, which) -> {
				isProcessingExcel = true; // Excel işlemi başlıyor
				btnNewStudy.setEnabled(false); // NEW STUDY butonunu devre dışı bırak

				// Önce UI'ı güncelle
				btnNewStudy.setVisibility(View.VISIBLE);
				btnRgbToConcentration.setVisibility(View.VISIBLE);
				btnCalibrate.setVisibility(View.VISIBLE);
				previewView.setVisibility(View.GONE);
				rectangleView.setVisibility(View.GONE);
				captureButton.setVisibility(View.GONE);
				photoRecyclerView.setVisibility(View.GONE);
				btnFinishStudy.setVisibility(View.GONE);

				// Kamerayı kapat
				ProcessCameraProvider.getInstance(this).addListener(() -> {
					try {
						ProcessCameraProvider cameraProvider = 
							ProcessCameraProvider.getInstance(this).get();
						cameraProvider.unbindAll();
					} catch (Exception e) {
						Log.e("CameraX", "Failed to unbind camera uses cases", e);
					}
				}, ContextCompat.getMainExecutor(this));

				// İşlem devam ediyor dialogu göster
				AlertDialog processingDialog = new AlertDialog.Builder(this)
					.setTitle("Processing Photos")
					.setMessage("Please wait while photos are being analyzed...")
					.setCancelable(false)
					.create();
				processingDialog.show();

				// Arka planda fotoğrafları işle
				new Thread(() -> {
					try {
						for (PhotoData photoData : pendingPhotos) {
							int previewWidth = photoData.bitmap.getWidth();
							int previewHeight = photoData.bitmap.getHeight();
							
							// Dikdörtgen boyutlarını hesapla
							int rectWidth = previewWidth / 3;  // Örnek boyut
							int rectHeight = previewHeight / 2; // Örnek boyut
							int bitmapX = (previewWidth - rectWidth) / 2;
							int bitmapY = (previewHeight - rectHeight) / 2;
							
							Bitmap croppedBitmap = Bitmap.createBitmap(
								photoData.bitmap, 
								bitmapX, 
								bitmapY, 
								rectWidth, 
								rectHeight
							);
							
							List<ColorProcessor.ColorPoint> points = 
								ColorProcessor.processRectangleArea(croppedBitmap, 0, 0, 
									croppedBitmap.getWidth(), croppedBitmap.getHeight());
									
							ColorProcessor.ColorCalculations calculations = 
								new ColorProcessor.ColorCalculations(points);
							
							excelManager.addData(photoData.photoFile.getName(), calculations);
							
							if (croppedBitmap != photoData.bitmap) {
								croppedBitmap.recycle();
							}
							photoData.bitmap.recycle();
						}

						// Excel dosyasını kaydet ve kapat
						if (excelManager != null) {
							excelManager.close();
						}

						pendingPhotos.clear(); // İşlenmiş fotoğrafları temizle

						runOnUiThread(() -> {
							processingDialog.dismiss();
							Toast.makeText(MainActivity.this, 
								"Study completed successfully", 
								Toast.LENGTH_SHORT).show();
							isProcessingExcel = false; // Excel işlemi bitti
							btnNewStudy.setEnabled(true); // NEW STUDY butonunu aktif et
						});

					} catch (Exception e) {
						runOnUiThread(() -> {
							processingDialog.dismiss();
							Toast.makeText(MainActivity.this, 
								"Error processing photos: " + e.getMessage(), 
								Toast.LENGTH_LONG).show();
							isProcessingExcel = false; // Excel işlemi bitti (hata ile)
							btnNewStudy.setEnabled(true); // NEW STUDY butonunu aktif et
						});
						e.printStackTrace();
					}
				}).start();

			})
			.setNegativeButton("No", null)
			.show();
	}

	private void setupTapToFocus() {
		previewView.setOnTouchListener((v, event) -> {
			if (event.getAction() != MotionEvent.ACTION_DOWN) {
				return false;
			}

			if (camera == null) {
				return false;
			}

			MeteringPointFactory factory = new SurfaceOrientedMeteringPointFactory(
				previewView.getWidth(),
				previewView.getHeight()
			);

			MeteringPoint point = factory.createPoint(event.getX(), event.getY());

			FocusMeteringAction action = new FocusMeteringAction.Builder(point)
				.setAutoCancelDuration(5, TimeUnit.SECONDS)
				.build();

			camera.getCameraControl().startFocusAndMetering(action)
				.addListener(() -> {
					runOnUiThread(() -> {
						showFocusIndicator(event.getX(), event.getY());
					});
				}, ContextCompat.getMainExecutor(this));

			return true;
		});
	}

	@Override
	public void onBackPressed() {
		if (previewView.getVisibility() == View.VISIBLE) {
			// Eğer kamera açıksa ve çalışma devam ediyorsa
			new AlertDialog.Builder(this)
				.setTitle("Finish Study")
				.setMessage("Do you want to finish this study?")
				.setPositiveButton("Yes", (dialog, which) -> {
					try {
						if (excelManager != null) {
							excelManager.close();
						}

						// Çalışmayı temizle
						clearCurrentStudy();
						
						// Ana menüyü göster
						btnNewStudy.setVisibility(View.VISIBLE);
						btnRgbToConcentration.setVisibility(View.VISIBLE);
						btnCalibrate.setVisibility(View.VISIBLE);
						
						// Kamera ile ilgili görünümleri gizle
						previewView.setVisibility(View.GONE);
						rectangleView.setVisibility(View.GONE);
						captureButton.setVisibility(View.GONE);
						photoRecyclerView.setVisibility(View.GONE);
						btnFinishStudy.setVisibility(View.GONE);

						// Başarı mesajı göster
						Toast.makeText(this, 
							"Study completed successfully", 
							Toast.LENGTH_SHORT).show();

						// Kamerayı kapat
						ProcessCameraProvider.getInstance(this).addListener(() -> {
							try {
								ProcessCameraProvider cameraProvider = 
									ProcessCameraProvider.getInstance(this).get();
								cameraProvider.unbindAll();
							} catch (Exception e) {
								Log.e("CameraX", "Failed to unbind camera uses cases", e);
							}
						}, ContextCompat.getMainExecutor(this));

					} catch (IOException e) {
						Toast.makeText(this, 
							"Error closing study: " + e.getMessage(), 
							Toast.LENGTH_LONG).show();
						e.printStackTrace();
					}
				})
				.setNegativeButton("No", null)
				.setOnCancelListener(dialog -> {
					// Dialog iptal edilirse veya dışarı tıklanırsa hiçbir şey yapma
				})
				.show();
		} else {
			// Eğer ana menüdeyse normal geri tuşu davranışını uygula
			super.onBackPressed();
		}
	}

	private void showFocusIndicator(float x, float y) {
		// Odak göstergesi için yeni view oluştur
		ImageView focusIndicator = new ImageView(this);
		GradientDrawable circle = new GradientDrawable();
		circle.setShape(GradientDrawable.OVAL);
		circle.setStroke(4, Color.WHITE);
		circle.setColor(Color.TRANSPARENT);
		
		focusIndicator.setImageDrawable(circle);
		
		FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(100, 100);
		params.leftMargin = (int) (x - 50);
		params.topMargin = (int) (y - 50);
		focusIndicator.setLayoutParams(params);
		
		cameraOverlay.addView(focusIndicator);
		
		// Odak animasyonu
		focusIndicator.animate()
			.scaleX(0.5f)
			.scaleY(0.5f)
			.alpha(0)
			.setDuration(300)
			.withEndAction(() -> cameraOverlay.removeView(focusIndicator))
			.start();
	}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initializeViews();
        setupInitialVisibility();
        setupClickListeners();
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
    }

	private void initializeViews() {
		btnNewStudy = findViewById(R.id.btnNewStudy);
		btnRgbToConcentration = findViewById(R.id.btnRgbToConcentration);
		btnCalibrate = findViewById(R.id.btnCalibrate);
		cameraOverlay = findViewById(R.id.cameraOverlay);
		previewView = findViewById(R.id.previewView);
		photoRecyclerView = findViewById(R.id.photoRecyclerView);

		photoAdapter = new PhotoAdapter(this);
		photoRecyclerView.setAdapter(photoAdapter);
		photoRecyclerView.setLayoutManager(
			new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

		setupRectangleView();
		setupCaptureButton();
		setupPhotoRecyclerView();
		setupFinishStudyButton(); // Burada çağrılmalı
	}
	private void setupCaptureButtonDrawable() {
		LayerDrawable drawable = createCaptureButtonDrawable();
		captureButton.setImageDrawable(drawable);
		captureButton.setOnClickListener(v -> takePhoto());
	}

    private void setupRectangleView() {
        rectangleView = new ImageView(this);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setStroke(4, Color.parseColor("#66ff00"));
        shape.setColor(Color.TRANSPARENT);

        int width = (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_MM, 5, getResources().getDisplayMetrics());
        int height = (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_MM, 10, getResources().getDisplayMetrics());

        FrameLayout.LayoutParams rectParams = new FrameLayout.LayoutParams(width, height);
        rectParams.gravity = android.view.Gravity.CENTER;
        rectangleView.setLayoutParams(rectParams);
        rectangleView.setBackground(shape);
        rectangleView.setVisibility(View.GONE);

        cameraOverlay.addView(rectangleView);
    }

    private void setupCaptureButton() {
        captureButton = new ImageButton(this);
        int buttonSize = (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 80, getResources().getDisplayMetrics());

        FrameLayout.LayoutParams buttonParams = new FrameLayout.LayoutParams(buttonSize, buttonSize);
        buttonParams.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL;
        buttonParams.bottomMargin = (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 40, getResources().getDisplayMetrics());

        captureButton.setLayoutParams(buttonParams);
        captureButton.setBackground(createCaptureButtonDrawable());
        captureButton.setOnClickListener(v -> takePhoto());
        captureButton.setVisibility(View.GONE);

        cameraOverlay.addView(captureButton);
    }

    private void setupPhotoRecyclerView() {
        FrameLayout.LayoutParams recyclerParams = new FrameLayout.LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT);
        recyclerParams.gravity = android.view.Gravity.BOTTOM;
        recyclerParams.bottomMargin = (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 140, getResources().getDisplayMetrics());
        photoRecyclerView.setLayoutParams(recyclerParams);
    }

	private void setupFinishStudyButton() {
		btnFinishStudy = findViewById(R.id.btnFinishStudy);
		if (btnFinishStudy == null) {
			btnFinishStudy = new Button(this);
			btnFinishStudy.setText("FINISH WORK");
			btnFinishStudy.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_dark));
			btnFinishStudy.setTextColor(Color.WHITE);
			
			FrameLayout.LayoutParams finishParams = new FrameLayout.LayoutParams(
				LayoutParams.WRAP_CONTENT,
				LayoutParams.WRAP_CONTENT);
			finishParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
			finishParams.topMargin = (int) TypedValue.applyDimension(
				TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics());
			btnFinishStudy.setLayoutParams(finishParams);
			btnFinishStudy.setVisibility(View.GONE);
			
			cameraOverlay.addView(btnFinishStudy);
		}

		btnFinishStudy.setOnClickListener(v -> {
			new AlertDialog.Builder(this)
				.setTitle("Finish Study")
				.setMessage("Do you want to finish this study?")
				.setPositiveButton("Yes", (dialog, which) -> {
					try {
						if (excelManager != null) {
							excelManager.close();
						}

						clearCurrentStudy();
						
						btnNewStudy.setVisibility(View.VISIBLE);
						btnRgbToConcentration.setVisibility(View.VISIBLE);
						btnCalibrate.setVisibility(View.VISIBLE);
						previewView.setVisibility(View.GONE);
						rectangleView.setVisibility(View.GONE);
						captureButton.setVisibility(View.GONE);
						photoRecyclerView.setVisibility(View.GONE);
						btnFinishStudy.setVisibility(View.GONE);

						Toast.makeText(this, 
							"Study completed successfully", 
							Toast.LENGTH_SHORT).show();

						ProcessCameraProvider.getInstance(this).addListener(() -> {
							try {
								ProcessCameraProvider cameraProvider = 
									ProcessCameraProvider.getInstance(this).get();
								cameraProvider.unbindAll();
							} catch (Exception e) {
								Log.e("CameraX", "Failed to unbind camera uses cases", e);
							}
						}, ContextCompat.getMainExecutor(this));

					} catch (IOException e) {
						Toast.makeText(this, 
							"Error closing study: " + e.getMessage(), 
							Toast.LENGTH_LONG).show();
						e.printStackTrace();
					}
				})
				.setNegativeButton("No", null)
				.show();
		});
	}

    private void setupInitialVisibility() {
        btnNewStudy.setVisibility(View.VISIBLE);
        btnRgbToConcentration.setVisibility(View.VISIBLE);
        btnCalibrate.setVisibility(View.VISIBLE);
        rectangleView.setVisibility(View.GONE);
        previewView.setVisibility(View.GONE);
        photoRecyclerView.setVisibility(View.GONE);
        btnFinishStudy.setVisibility(View.GONE);
        captureButton.setVisibility(View.GONE);
    }

    private void setupClickListeners() {
        btnNewStudy.setOnClickListener(v -> showStudyNameDialog());
        btnRgbToConcentration.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, RGBToConcentrationActivity.class);
            startActivity(intent);
        });
        btnCalibrate.setOnClickListener(v -> {
            // Kalibrasyon işlemleri
        });
    }

    private LayerDrawable createCaptureButtonDrawable() {
        GradientDrawable outerCircle = new GradientDrawable();
        outerCircle.setShape(GradientDrawable.OVAL);
        outerCircle.setStroke((int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 3, 
            getResources().getDisplayMetrics()), Color.WHITE);
        outerCircle.setColor(Color.TRANSPARENT);

        GradientDrawable innerCircle = new GradientDrawable();
        innerCircle.setShape(GradientDrawable.OVAL);
        innerCircle.setColor(Color.WHITE);

        LayerDrawable layerDrawable = new LayerDrawable(
            new GradientDrawable[]{outerCircle, innerCircle});

        int padding = (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 8, 
            getResources().getDisplayMetrics());
        layerDrawable.setLayerInset(1, padding, padding, padding, padding);

        return layerDrawable;
    }

    private void takePhoto() {
        if (imageCapture == null) return;

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            .format(new Date());
        File photoFile = new File(currentStudyFolder, "IMG_" + timestamp + ".jpg");

        ImageCapture.OutputFileOptions outputFileOptions = 
            new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputFileOptions, 
            ContextCompat.getMainExecutor(this),
            new ImageCapture.OnImageSavedCallback() {
                @Override
                public void onImageSaved(ImageCapture.OutputFileResults outputFileResults) {
                    photoAdapter.addPhoto(photoFile);
                    processPhoto(photoFile);
                }

                @Override
                public void onError(ImageCaptureException error) {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, 
                            "Error taking photo: " + error.getMessage(), 
                            Toast.LENGTH_LONG).show();
                    });
                }
            });
    }

	private void showSamplingPoints(int x, int y, int width, int height) {
		// Örnekleme noktalarını ekranda göster
		int pointCount = 9; // 3x3 grid
		int spacing = Math.min(width, height) / 4;
		
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				float pointX = x + (i + 1) * spacing;
				float pointY = y + (j + 1) * spacing;
				
				ImageView point = new ImageView(this);
				GradientDrawable cross = new GradientDrawable();
				
				// Her satır için farklı renk
				int color;
				if (i == 0) color = Color.RED;
				else if (i == 1) color = Color.GREEN;
				else color = Color.BLUE;
				
				// Artı şeklinde çizim
				drawCross(point, color);
				
				FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(20, 20);
				params.leftMargin = (int) pointX - 10;
				params.topMargin = (int) pointY - 10;
				point.setLayoutParams(params);
				
				cameraOverlay.addView(point);
				
				// 2 saniye sonra noktaları kaldır
				new Handler().postDelayed(() -> {
					cameraOverlay.removeView(point);
				}, 2000);
			}
		}
	}

	private void drawCross(ImageView view, int color) {
		Paint paint = new Paint();
		paint.setColor(color);
		paint.setStrokeWidth(4);
		paint.setStyle(Paint.Style.STROKE);
		
		Bitmap bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmap);
		
		// Yatay çizgi
		canvas.drawLine(0, 10, 20, 10, paint);
		// Dikey çizgi
		canvas.drawLine(10, 0, 10, 20, paint);
		
		view.setImageBitmap(bitmap);
	}

    private void startCamera() {
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

	private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
		try {
			cameraProvider.unbindAll();

			Preview preview = new Preview.Builder()
				.setTargetRotation(previewView.getDisplay().getRotation())
				.setTargetResolution(new Size(720, 1280))
				.build();

			imageCapture = new ImageCapture.Builder()
				.setTargetRotation(previewView.getDisplay().getRotation())
				.setTargetResolution(new Size(720, 1280))
				.build();

			CameraSelector cameraSelector = new CameraSelector.Builder()
				.requireLensFacing(CameraSelector.LENS_FACING_BACK)
				.build();

			preview.setSurfaceProvider(previewView.getSurfaceProvider());

			camera = cameraProvider.bindToLifecycle(
				this,
				cameraSelector,
				preview,
				imageCapture
			);

			// Zoom'u devre dışı bırak
			camera.getCameraControl().setZoomRatio(1.0f);
			camera.getCameraInfo().getZoomState().observe(this, state -> {
				if (state.getZoomRatio() != 1.0f) {
					camera.getCameraControl().setZoomRatio(1.0f);
				}
			});

			// Manuel odaklama için listener ekle
			setupTapToFocus();

		} catch (Exception e) {
			Log.e("CameraX", "Use case binding failed", e);
			runOnUiThread(() -> {
				Toast.makeText(MainActivity.this,
					"Camera initialization failed: " + e.getMessage(),
					Toast.LENGTH_LONG).show();
			});
		}
	}

    private void checkPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA);
        }
        
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) 
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
        
        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, 
                permissionsToRequest.toArray(new String[0]), 
                STORAGE_PERMISSION_CODE);
        } else {
            showCamera();
        }
    }

	private boolean isStudyActive = false; // Sınıf değişkeni olarak ekle

	@Override
	protected void onResume() {
		super.onResume();
		if (isStudyActive && previewView.getVisibility() == View.VISIBLE) {
			startCamera();
		}
	}

	private void showCamera() {
		isStudyActive = true; // Kamera gösterildiğinde true yap
		btnNewStudy.setVisibility(View.GONE);
		btnRgbToConcentration.setVisibility(View.GONE);
		btnCalibrate.setVisibility(View.GONE);
		previewView.setVisibility(View.VISIBLE);
		rectangleView.setVisibility(View.VISIBLE);
		captureButton.setVisibility(View.VISIBLE);
		photoRecyclerView.setVisibility(View.VISIBLE);
		btnFinishStudy.setVisibility(View.VISIBLE);
		startCamera();
	}

    @Override
    public void onRequestPermissionsResult(int requestCode, 
            @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == STORAGE_PERMISSION_CODE) {
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }
            
            if (allPermissionsGranted) {
                showCamera();
            } else {
                Toast.makeText(this, 
                    "Storage permissions are required for this app", 
                    Toast.LENGTH_LONG).show();
            }
        }
    }

	private void clearCurrentStudy() {
		isStudyActive = false; // Çalışma bittiğinde false yap
		if (photoAdapter != null) {
			photoAdapter.clearPhotos();
			photoRecyclerView.getAdapter().notifyDataSetChanged();
		}

		if (excelManager != null) {
			try {
				excelManager.close();
				excelManager = null;
			} catch (IOException e) {
				Log.e("MainActivity", "Error closing Excel file", e);
			}
		}

		currentStudyFolder = null;
	}

    private void createStudyFolder(String folderName) {
        clearCurrentStudy();

        File documentsDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOCUMENTS);
        File cameraRGBDir = new File(documentsDir, "CameraRGB");
        File studyDir = new File(cameraRGBDir, folderName);
        File photosDir = new File(studyDir, "photos");

        if (!studyDir.exists() && !studyDir.mkdirs()) {
            Toast.makeText(this, 
                "Failed to create study directory", 
                Toast.LENGTH_LONG).show();
            return;
        }

        if (!photosDir.exists() && !photosDir.mkdirs()) {
            Toast.makeText(this, 
                "Failed to create photos directory", 
                Toast.LENGTH_LONG).show();
            return;
        }

        currentStudyFolder = photosDir.getAbsolutePath();
        excelManager = new ExcelManager(studyDir.getAbsolutePath());
        
        photoAdapter = new PhotoAdapter(this);
        photoRecyclerView.setAdapter(photoAdapter);
    }


    private void showStudyNameDialog() {
        final EditText input = new EditText(this);
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Enter Study Name")
            .setView(input)
            .setPositiveButton("OK", null)
            .setNegativeButton("Cancel", null)
            .create();

        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String studyName = input.getText().toString().trim();
                if (studyName.isEmpty()) {
                    input.setError("Study name is required");
                    return;
                }

                File studyDir = new File(
                    Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOCUMENTS),
                    "CameraRGB/" + studyName);

                if (studyDir.exists()) {
                    input.setError("A study with this name already exists");
                    return;
                }

                createStudyFolder(studyName);
                dialog.dismiss();
                checkPermissions();
            });
        });

        dialog.show();
    }


    @Override
    protected void onPause() {
        super.onPause();
        if (previewView.getVisibility() == View.VISIBLE) {
            ProcessCameraProvider.getInstance(this).addListener(() -> {
                try {
                    ProcessCameraProvider cameraProvider = 
                        ProcessCameraProvider.getInstance(this).get();
                    cameraProvider.unbindAll();
                } catch (Exception e) {
                    Log.e("CameraX", "Failed to unbind camera uses cases", e);
                }
            }, ContextCompat.getMainExecutor(this));
        }
    }
}