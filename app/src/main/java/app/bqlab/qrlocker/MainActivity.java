package app.bqlab.qrlocker;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextPaint;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.Toast;

import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    //constants
    final String LOCKER_OPEN = "1";
    final String LOCKER_LOCK = "2";
    //variables
    boolean master;
    boolean deviceConnected;
    String deviceAddress;
    //objects
    BluetoothSPP mBluetooth;
    SharedPreferences mKeyPref;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
        connectDevice();
        requestPermission();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (resultCode == Activity.RESULT_CANCELED) {
            switch (requestCode) {
                case BluetoothState.REQUEST_ENABLE_BT:
                    Toast.makeText(this, "블루투스를 사용할 수 없습니다.", Toast.LENGTH_LONG).show();
                    break;
            }
        } else {
            switch (requestCode) {
                case BluetoothState.REQUEST_ENABLE_BT:
                    connectDevice();
                    break;
            }
        }
    }

    private void init() {
        //initialize
        mBluetooth = new BluetoothSPP(this);
        mKeyPref = getSharedPreferences("KEYS", MODE_PRIVATE);
        //events
        findViewById(R.id.main_scan).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, ScanActivity.class));
                finish();
            }
        });
        findViewById(R.id.main_state).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String state = deviceConnected ? "연결됨" : "연결되지 않음";
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage("블루투스 상태: " + state)
                        .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        }).show();
            }
        });
        findViewById(R.id.main_password).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (deviceConnected)
                    showEditPasswordDialog();
                else
                    Toast.makeText(MainActivity.this, "사물함과 연결되어 있지 않습니다.", Toast.LENGTH_LONG).show();
            }
        });
        findViewById(R.id.main_master).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage("마스터 키를 사용할까요?")
                        .setNegativeButton("취소", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        })
                        .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (!master) {
                                    Toast.makeText(MainActivity.this, "마스터 키를 사용합니다.", Toast.LENGTH_LONG).show();
                                    master = true;
                                } else
                                    Toast.makeText(MainActivity.this, "이미 마스터 키를 사용하고 있습니다.", Toast.LENGTH_LONG).show();
                            }
                        })
                        .setNeutralButton("마스터 키 해제", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Toast.makeText(MainActivity.this, "마스터 키를 사용하지 않습니다.", Toast.LENGTH_LONG).show();
                                master = false;
                            }
                        })
                        .show();
            }
        });
    }

    private void connectDevice() {
        deviceAddress = getIntent().getStringExtra("DEVICE_ADDRESS");
        if (deviceAddress != null) {
            if (!mBluetooth.isBluetoothAvailable()) {
                Toast.makeText(this, "블루투스를 지원하지 않는 기기입니다.", Toast.LENGTH_LONG).show();
                finishAffinity();
            } else if (!mBluetooth.isBluetoothEnabled()) {
                startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), BluetoothState.REQUEST_ENABLE_BT);
            } else if (!mBluetooth.isServiceAvailable()) {
                mBluetooth.setupService();
                mBluetooth.startService(BluetoothState.DEVICE_OTHER);
                connectDevice();
            } else {
                mBluetooth.setBluetoothConnectionListener(new BluetoothSPP.BluetoothConnectionListener() {
                    @Override
                    public void onDeviceConnected(String name, final String address) {
                        deviceConnected = true;
                        if (isPasswordSet()) {
                            mBluetooth.send(LOCKER_OPEN, true);
                            showSetPasswordDialog();
                        } else
                            showCheckPasswordDialog();
                    }

                    @Override
                    public void onDeviceDisconnected() {
                        deviceConnected = false;
                        Log.d("MainActivity", "Disconnected: " + deviceAddress);
                    }

                    @Override
                    public void onDeviceConnectionFailed() {
                        deviceConnected = false;
                        Log.d("MainActivity", "Fail to connect: " + deviceAddress);
                        Toast.makeText(MainActivity.this, "사물함과 연결할 수 없습니다.", Toast.LENGTH_LONG).show();
                    }
                });
                try {
                    mBluetooth.connect(deviceAddress);
                    Toast.makeText(this, "사물함과 연결하는 중입니다.", Toast.LENGTH_LONG).show();
                } catch (IllegalArgumentException e) {
                    Log.d("MainActivity", "IllegalArgumentException");
                    Toast.makeText(MainActivity.this, "잘못된 형식의 QR 코드입니다.", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private void showSetPasswordDialog() {
        final EditText passwordInput = new EditText(this);
        passwordInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        passwordInput.setSingleLine();
        new AlertDialog.Builder(this)
                .setCancelable(true)
                .setTitle("잠금 설정")
                .setMessage("비밀번호를 설정하세요.")
                .setView(passwordInput)
                .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (deviceConnected) {
                            String input = passwordInput.getText().toString();
                            if (input.isEmpty()) {
                                Toast.makeText(MainActivity.this, "비밀번호를 입력하세요.", Toast.LENGTH_LONG).show();
                            } else {
                                mBluetooth.send(LOCKER_LOCK, true);
                                mKeyPref.edit().putString(deviceAddress, input).apply();
                                Log.d("MainActivity", deviceAddress + "'s password set to" + input);
                            }
                        } else {
                            Log.d("MainActivity", "Fail to set password");
                            Toast.makeText(MainActivity.this, "사물함과 연결되어 있지 않습니다.", Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        if (!isPasswordSet()) {
                            Toast.makeText(MainActivity.this, "사물함을 사용하지 않습니다.", Toast.LENGTH_LONG).show();
                            Log.d("MainActivity", "Not input password");
                            mBluetooth.disconnect();
                        }
                    }
                }).show();
    }

    private void showCheckPasswordDialog() {
        final EditText passwordInput = new EditText(this);
        passwordInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        passwordInput.setSingleLine();
        new AlertDialog.Builder(this)
                .setCancelable(true)
                .setTitle("잠금 해제")
                .setMessage("설정한 비밀번호를 입력하세요.")
                .setView(passwordInput)
                .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (deviceConnected) {
                            if (master) {
                                Toast.makeText(MainActivity.this, "잠금이 해제됩니다.", Toast.LENGTH_LONG).show();
                                mKeyPref.edit().putString(deviceAddress, "").apply();
                                mBluetooth.send(LOCKER_OPEN, true);
                                mBluetooth.disconnect();
                            } else {
                                String input = passwordInput.getText().toString();
                                if (isPasswordRight(input)) {
                                    Toast.makeText(MainActivity.this, "잠금이 해제됩니다.", Toast.LENGTH_LONG).show();
                                    mKeyPref.edit().putString(deviceAddress, "").apply();
                                    mBluetooth.send(LOCKER_OPEN, true);
                                    mBluetooth.disconnect();
                                } else {
                                    Log.d("MainActivity", "Wrong password input");
                                    Toast.makeText(MainActivity.this, "비밀번호가 틀립니다.", Toast.LENGTH_LONG).show();
                                }
                            }
                        } else {
                            Log.d("MainActivity", "Fail to set password");
                            Toast.makeText(MainActivity.this, "사물함과 연결되어 있지 않습니다.", Toast.LENGTH_LONG).show();
                        }
                    }
                }).show();
    }

    private void showEditPasswordDialog() {
        final EditText passwordInput = new EditText(this);
        passwordInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        passwordInput.setSingleLine();
        new AlertDialog.Builder(this)
                .setCancelable(true)
                .setTitle("비밀번호 변경")
                .setMessage("설정한 비밀번호를 입력하세요.")
                .setView(passwordInput)
                .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (deviceConnected) {
                            if (master) {
                                Toast.makeText(MainActivity.this, "마스터 키를 사용중입니다.", Toast.LENGTH_LONG).show();
                            } else {
                                String input = passwordInput.getText().toString();
                                if (input.isEmpty()) {
                                    Toast.makeText(MainActivity.this, "비밀번호를 입력하세요.", Toast.LENGTH_LONG).show();
                                } else {
                                    if (isPasswordRight(input)) {
                                        mKeyPref.edit().putString(deviceAddress, input).apply();
                                        Log.d("MainActivity", deviceAddress + "'s password edited to" + input);
                                    } else {
                                        Log.d("MainActivity", "Wrong password input");
                                        Toast.makeText(MainActivity.this, "비밀번호가 틀립니다.", Toast.LENGTH_LONG).show();
                                    }
                                }
                            }
                        } else {
                            Log.d("MainActivity", "Fail to set password");
                            Toast.makeText(MainActivity.this, "사물함과 연결되어 있지 않습니다.", Toast.LENGTH_LONG).show();
                        }
                    }
                }).show();
    }

    private boolean isPasswordSet() {
        boolean b = Objects.equals(mKeyPref.getString(deviceAddress, ""), "");
        Log.d("MainActivity", "Address: " + deviceAddress);
        Log.d("MainActivity", "Address PW: " + String.valueOf(b));
        return b;
    }

    private boolean isPasswordRight(String input) {
        boolean b = Objects.equals(mKeyPref.getString(deviceAddress, ""), input);
        Log.d("MainActivity", "Original PW: " + mKeyPref.getString(deviceAddress, ""));
        Log.d("MainActivity", "Inputted PW: " + input);
        return b;
    }

    private void requestPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 0);
        }
    }
}
