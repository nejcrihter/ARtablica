package com.example.artablica;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.squareup.picasso.Picasso;

import org.openalpr.OpenALPR;
import org.openalpr.model.Candidate;
import org.openalpr.model.Result;
import org.openalpr.model.Results;
import org.openalpr.model.ResultsError;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private final int REQUEST_IMAGE = 100;
    private final int REQUEST_FILE = 42;
    private final int STORAGE = 1;

    private String ANDROID_DATA_DIR;
    private File imgFolder;
    private File imageFile;

    private Context appCtx;
    private ImageView imageView;
    private EditText txtCountry;
    private EditText txtRegion;
    private EditText txtCandidatesNum;
    private TableLayout resultTable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkPermission();

        appCtx = this;
        ANDROID_DATA_DIR = this.getApplicationInfo().dataDir;

        //txtCandidatesNum = (EditText) findViewById(R.id.txtCandidatesNum);
        /*txtCountry = (EditText) findViewById(R.id.txtCountry);
        txtRegion = (EditText) findViewById(R.id.txtRegion);*/

        resultTable = (TableLayout) findViewById(R.id.resultTable);
        imageView = (ImageView) findViewById(R.id.imageView);

        findViewById(R.id.btnTakePicture).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                takePicture();
            }
        });

        findViewById(R.id.btnLoad).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                loadPicture();
            }
        });

        findViewById(R.id.btnClear).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //resultTable.setVisibility(View.GONE);
                int count = resultTable.getChildCount();
                // Start from 1 if you want to keep the header row, otherwise start from 0
                for (int i = count - 1; i >= 0; i--) {
                    View child = resultTable.getChildAt(i);
                    if (child instanceof TableRow) {
                        resultTable.removeViewAt(i);
                    }
                }

                Toast.makeText(appCtx, "Result table cleared!", Toast.LENGTH_LONG).show();
            }
        });

        /*findViewById(R.id.btnFlushDir).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int cont = 0;
                for (File file : imgFolder.listFiles()) {
                    if (file.delete()) ++cont;
                }
                Toast.makeText(appCtx, cont + " files deleted successfully!", Toast.LENGTH_LONG).show();
            }
        });*/

        File imgFolder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // For Android 10 and above, use the app-specific directory
            imgFolder = new File(getExternalFilesDir(null), "OpenALPR");
        } else {
            // For older versions, use the external storage directory
            imgFolder = new File(Environment.getExternalStorageDirectory(), "OpenALPR");
        }

        if (!imgFolder.exists()) {
            boolean isCreated = imgFolder.mkdirs(); // Use mkdirs() instead of mkdir()
            if (!isCreated) {
                Log.e("Directory Creation", "Failed to create directory");
            } else {
                Log.d("Directory Creation", "Created directory");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ((requestCode == REQUEST_IMAGE || requestCode == REQUEST_FILE) && resultCode == Activity.RESULT_OK) {
            final long startTime = System.currentTimeMillis();
            final long[] endTime = new long[1];
            final ProgressDialog progress = ProgressDialog.show(this, "Loading", "Parsing result...", true);
            final String openAlprConfFile = ANDROID_DATA_DIR + File.separatorChar + "runtime_data" + File.separatorChar + "openalpr.conf";
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 10;

            if (requestCode == REQUEST_FILE) {
                if (data != null && data.getData() != null) {
                    Uri contentUri = data.getData();
                    InputStream inputStream = null;
                    try {
                        inputStream = getContentResolver().openInputStream(contentUri);
                    } catch (FileNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                    File outputDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES); // Or use getFilesDir() for internal storage
                    File outputFile = new File(outputDir, "copied_image.jpg"); // Give a unique name for the file
                    OutputStream outputStream = null;
                    try {
                        outputStream = new FileOutputStream(outputFile);
                    } catch (FileNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                    byte[] buffer = new byte[1024];
                    int length;
                    while (true) {
                        try {
                            if (!((length = inputStream.read(buffer)) > 0)) break;
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        try {
                            outputStream.write(buffer, 0, length);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    try {
                        outputStream.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    String path = Environment.getExternalStorageDirectory().getPath() + "/" + data.getData().getLastPathSegment().split(":")[1];
                    Log.d("path", outputFile.getAbsolutePath());
                    imageFile = new File(outputFile.getAbsolutePath());
                    Picasso.get().invalidate(imageFile);
                }
            }

            final int[] x1 = {0};
            final int[] x2 = {0};
            final int[] y1 = {0};
            final int[] y2 = {0};
            final String[] plate = {""};

            AsyncTask.execute(new Runnable() {

                @Override
                public void run() {

                    //int candidates = txtCandidatesNum.getText().toString().isEmpty() ? 5 : Integer.parseInt((txtCandidatesNum.getText().toString()));
                    //String region = txtRegion.getText().toString().isEmpty() ? "eu" : (txtCandidatesNum.getText().toString());
                    //String country = txtCountry.getText().toString().isEmpty() ? "" : (txtCandidatesNum.getText().toString());

                    String result;
                    try {
                        result = OpenALPR.Factory.create(MainActivity.this, ANDROID_DATA_DIR).recognizeWithCountryRegionNConfig("eu", "", imageFile.getAbsolutePath(), openAlprConfFile, 1);
                    } catch (Exception e) {
                        Log.d("test", e.toString());
                        throw e;
                    }
                    Log.d("OPEN ALPR", result);

                    try {
                        final Results results = new Gson().fromJson(result, Results.class);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                resultTable.setVisibility(View.VISIBLE);
                                if (results == null || results.getResults() == null || results.getResults().size() == 0) {
                                    Toast.makeText(MainActivity.this, "It was not possible to detect the licence plate.", Toast.LENGTH_LONG).show();
                                } else {
                                    endTime[0] = System.currentTimeMillis();
                                    TableLayout.LayoutParams rowLayoutParams = new TableLayout.LayoutParams(TableLayout.LayoutParams.FILL_PARENT, TableLayout.LayoutParams.WRAP_CONTENT);
                                    TableRow.LayoutParams cellLayoutParams = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);

                                    List<Result> resultsList = results.getResults();

                                    OkHttpClient client = new OkHttpClient();
                                    Log.d("result", resultsList.get(0).getPlate());

                                    Request request = new Request.Builder()
                                            .url("https://aznvwdojybktymohxcbb.supabase.co/rest/v1/cars?license_plate=eq." + resultsList.get(0).getPlate())
                                            .get()
                                            .addHeader("apikey", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImF6bnZ3ZG9qeWJrdHltb2h4Y2JiIiwicm9sZSI6ImFub24iLCJpYXQiOjE3MDI0MTkxMDIsImV4cCI6MjAxNzk5NTEwMn0.r5Ap708h8y4kDzPUdR_yitbFGokVgPp2oKb6jXGSzNk")
                                            .addHeader("license_plate", "eq." + resultsList.get(0).getPlate())
                                            .addHeader("Cache-Control", "no-cache") // Disable caching
                                            .addHeader("Accept", "*/*")
                                            .addHeader("CF-Cache-Status", "DYNAMIC")
                                            .addHeader("Connection", "keep-alive")
                                            .build();

                                    client.newCall(request).enqueue(new Callback() {
                                        @Override
                                        public void onResponse(Call call, Response response) throws IOException {
                                            if (response.isSuccessful()) {
                                                Log.d("header", response.toString());
                                                String responseBody = response.body().string();

                                                Gson gson = new Gson();
                                                Type listType = new TypeToken<List<Car>>(){}.getType();
                                                Log.d("request", request.toString());
                                                Log.d("response", responseBody);
                                                List<Car> cars = gson.fromJson(responseBody, listType);
                                                Log.d("json", cars.toString());
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        if (cars == null || cars.size() == 0) {
                                                            Log.d("here", "prslo sm");
                                                            Toast.makeText(MainActivity.this, "The licence plate is not in our database.", Toast.LENGTH_LONG).show();
                                                        } else {
                                                            // Update UI elements here, e.g., add views to TableLayout
                                                            // This code runs on the main thread
                                                            String[][] carDetails = {
                                                                    {"Letnik", Integer.toString(cars.get(0).getYear_of_make())},
                                                                    {"Motor", cars.get(0).getEngine()},
                                                                    {"Tip", cars.get(0).getType()},
                                                                    {"Število sedežev", Integer.toString(cars.get(0).getNumber_of_seats())},
                                                                    {"Število vrat", Integer.toString(cars.get(0).getNumber_of_doors())},
                                                                    {"Max hitrost", cars.get(0).getMax_speed() + "km/h"},
                                                                    {"Teža", cars.get(0).getWeight() + "kg"},
                                                                    {"Dimenzije", cars.get(0).getDimensions()},
                                                                    {"Gorivo", cars.get(0).getFuel_type()}
                                                            };

                                                            for (String[] carDetail : carDetails) {
                                                                TableRow tableRow = new TableRow(appCtx);
                                                                tableRow.setLayoutParams(new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT));
                                                                tableRow.setGravity(Gravity.CENTER_VERTICAL);
                                                                TableLayout.LayoutParams layoutParams = new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT);
                                                                layoutParams.setMargins(0, 30, 0, 30);
                                                                tableRow.setLayoutParams(layoutParams);

                                                                for (String detail : carDetail) {
                                                                    TextView cellValue = new TextView(appCtx);
                                                                    cellValue.setTypeface(null, Typeface.BOLD);
                                                                    cellValue.setText(detail);
                                                                    cellValue.setSingleLine(false);
                                                                    cellValue.setMaxWidth(25);
                                                                    cellValue.setGravity(Gravity.CENTER_VERTICAL);
                                                                    cellValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18); // Set font size to 18sp or as desired
                                                                    cellValue.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT));
                                                                    tableRow.addView(cellValue);
                                                                }

                                                                resultTable.addView(tableRow);
                                                                resultTable.invalidate();
                                                            }
                                                        }
                                                    }
                                                });
                                            }
                                        }

                                        @Override
                                        public void onFailure(Call call, IOException e) {
                                            Log.d("exception", e.toString());
                                        }
                                    });
                                }
                            }
                        });

                    } catch (JsonSyntaxException exception) {
                        final ResultsError resultsError = new Gson().fromJson(result, ResultsError.class);

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(appCtx, resultsError.getMsg(), Toast.LENGTH_LONG).show();
                            }
                        });
                    }

                    progress.dismiss();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Picasso.get().load(imageFile).fit().centerCrop().into(imageView);
                            if (imageView.getDrawable() != null) {
                                Bitmap bitmap = ((BitmapDrawable) imageView.getDrawable()).getBitmap();
                                Bitmap originalBitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath(), new BitmapFactory.Options());

                                float viewWidth = bitmap.getWidth();
                                float viewHeigth = bitmap.getHeight();
                                float originalWidth = originalBitmap.getWidth();
                                float originalHeigth = originalBitmap.getHeight();

                                Canvas canvas = new Canvas(bitmap);
                                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                                paint.setColor(Color.GREEN);
                                paint.setStyle(Paint.Style.STROKE);
                                paint.setStrokeWidth(8);

                                // map rectangle coordinates to imageview
                                int p1_x = (int) ((x1[0] * viewWidth) / originalWidth);
                                int p1_y = (int) ((y1[0] * viewHeigth) / originalHeigth);
                                int p2_x = (int) ((x2[0] * viewWidth) / originalWidth);
                                int p2_y = (int) ((y2[0] * viewHeigth) / originalHeigth);
                                canvas.drawRect(new Rect(p1_x, p1_y, p2_x, p2_y), paint);

                                paint.setTextSize(75);
                                paint.setStyle(Paint.Style.FILL);
                                paint.setTypeface(Typeface.DEFAULT_BOLD);
                                paint.setColor(Color.YELLOW);
                                canvas.drawText(plate[0], p1_x, p1_y - 10, paint);
                                imageView.setImageBitmap(bitmap);
                            }
                        }
                    });
                }
            });
        }
    }

    private void checkPermission() {
        List<String> permissions = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (!permissions.isEmpty()) {
            Toast.makeText(this, "Storage access needed to manage the picture.", Toast.LENGTH_LONG).show();
            String[] params = permissions.toArray(new String[permissions.size()]);
            ActivityCompat.requestPermissions(this, params, STORAGE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case STORAGE: {
                Map<String, Integer> perms = new HashMap<>();
                // Initial
                perms.put(Manifest.permission.WRITE_EXTERNAL_STORAGE, PackageManager.PERMISSION_GRANTED);
                perms.put(Manifest.permission.READ_EXTERNAL_STORAGE, PackageManager.PERMISSION_GRANTED);
                // Fill with results
                for (int i = 0; i < permissions.length; i++)
                    perms.put(permissions[i], grantResults[i]);
                // Check for WRITE_EXTERNAL_STORAGE
                Boolean storage = perms.get(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
                Boolean storageR = perms.get(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
                if (storageR) {
                    // permission was granted, yay!
                } else {
                    // Permission Denied
                    Toast.makeText(this, "Storage permission is needed to analyse the picture.", Toast.LENGTH_LONG).show();
                }
            }
            default:
                break;
        }
    }

    public String dateToString(Date date, String format) {
        SimpleDateFormat df = new SimpleDateFormat(format, Locale.getDefault());

        return df.format(date);
    }

    public void takePicture() {
        // Generate the path for the next photo
        String name = dateToString(new Date(), "yyyy-MM-dd-hh-mm-ss");
        File imgFolder = new File(getExternalFilesDir(null), "OpenALPR");
        imageFile = new File(imgFolder, name + ".jpg");

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        Uri photoURI = FileProvider.getUriForFile(this,
                getApplicationContext().getPackageName() + ".provider",
                imageFile);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_IMAGE);
    }



    public void loadPicture() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_FILE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (imageFile != null) {// Picasso does not seem to have an issue with a null value, but to be safe
            Picasso.get().load(imageFile).fit().centerCrop().into(imageView);
        }
    }

}