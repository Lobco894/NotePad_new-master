/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.notepad;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.io.OutputStream;

/**
 * This Activity handles "editing" a note, where editing is responding to
 * {@link Intent#ACTION_VIEW} (request to view data), edit a note
 * {@link Intent#ACTION_EDIT}, create a note {@link Intent#ACTION_INSERT}, or
 * create a new note from the current contents of the clipboard {@link Intent#ACTION_PASTE}.
 *
 * NOTE: Notice that the provider operations in this Activity are taking place on the UI thread.
 * This is not a good practice. It is only done here to make the code more readable. A real
 * application should use the {@link android.content.AsyncQueryHandler}
 * or {@link android.os.AsyncTask} object to perform operations asynchronously on a separate thread.
 */
public class NoteEditor extends Activity {
    // For logging and debugging purposes
    private static final String TAG = "NoteEditor";

    /*
     * Creates a projection that returns the note ID and the note contents.
     */
    private static final String[] PROJECTION =
        new String[] {
            NotePad.Notes._ID,
            NotePad.Notes.COLUMN_NAME_TITLE,
            NotePad.Notes.COLUMN_NAME_NOTE
    };

    // A label for the saved state of the activity
    private static final String ORIGINAL_CONTENT = "origContent";

    // This Activity can be started by more than one action. Each action is represented
    // as a "state" constant
    private static final int STATE_EDIT = 0;
    private static final int STATE_INSERT = 1;

    // Global mutable variables
    private int mState;
    private Uri mUri;
    private Cursor mCursor;
    private EditText mText;
    private String mOriginalContent;

    public static class LinedEditText extends EditText {
        private Rect mRect;
        private Paint mPaint;

        public LinedEditText(Context context, AttributeSet attrs) {
            super(context, attrs);

            mRect = new Rect();
            mPaint = new Paint();
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setColor(0x800000FF);
        }

        @Override
        protected void onDraw(Canvas canvas) {

            int count = getLineCount();

            Rect r = mRect;
            Paint paint = mPaint;

            for (int i = 0; i < count; i++) {

                int baseline = getLineBounds(i, r);

                canvas.drawLine(r.left, baseline + 1, r.right, baseline + 1, paint);
            }

            super.onDraw(canvas);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.note_editor);

        ImageButton backBtn = (ImageButton) findViewById(R.id.backBtn);
        ImageButton settingBtn = (ImageButton) findViewById(R.id.setting_one);
        ImageButton chooseBtn = (ImageButton) findViewById(R.id.chooseBtn);
        ImageButton fontBtn = (ImageButton) findViewById(R.id.fontBtn);
        TextView sortBtn = (TextView) findViewById(R.id.sortBtn);

        fontBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showFontMenu(view);
            }
        });

        // 定义要查询的列
        String[] projection = { NotePad.Notes.COLUMN_NAME_TITLE };
        mUri = getIntent().getData();
        // 查询数据库，获取当前笔记的标题
        Cursor cursor = getContentResolver().query(
                mUri,       // 笔记的 URI
                projection, // 要查询的列
                null,       // 无需筛选条件
                null,       // 无需筛选参数
                null        // 无排序要求
        );

        // 检查 Cursor 是否有效并提取数据
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                String title = cursor.getString(
                        cursor.getColumnIndexOrThrow(NotePad.Notes.COLUMN_NAME_TITLE)
                );

                // 将标题设置到 TextView 中
                TextView titleView = (TextView) findViewById(R.id.title1);
                titleView.setText(title);
            }
            cursor.close(); // 关闭 Cursor 避免内存泄漏
        }



        sortBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showSortMenu(view);
            }
        });


        final EditText note =  (EditText) findViewById(R.id.note);
        final TextView length = (TextView) findViewById(R.id.length);
        // 设置监听器，实时更新字数
        note.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {
                // 不需要做处理
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int after) {
                // 更新字数
                int charCount = note.getText().length();
                length.setText(charCount + " 字丨");
            }

            @Override
            public void afterTextChanged(Editable editable) {
                // 不需要做处理
            }
        });

        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 结束当前活动并返回上一页面
                finish();
            }
        });

        settingBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 显示颜色菜单
                showColorMenu(v);
            }
        });

        chooseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showChooseMenu(view);
            }
        });

        //隐藏图标
        ActionBar actionBar = getActionBar();

        if (actionBar != null) {
            actionBar.hide();
        }

        final Intent intent = getIntent();
        final String action = intent.getAction();

        if (Intent.ACTION_EDIT.equals(action)) {

            mState = STATE_EDIT;
            mUri = intent.getData();

        } else if (Intent.ACTION_INSERT.equals(action)
                || Intent.ACTION_PASTE.equals(action)) {

            mState = STATE_INSERT;
            mUri = getContentResolver().insert(intent.getData(), null);

            if (mUri == null) {

                Log.e(TAG, "Failed to insert new note into " + getIntent().getData());

                finish();
                return;
            }

            setResult(RESULT_OK, (new Intent()).setAction(mUri.toString()));

        } else {

            Log.e(TAG, "Unknown action, exiting");
            finish();
            return;
        }

        mCursor = managedQuery(
            mUri,         // The URI that gets multiple notes from the provider.
            PROJECTION,   // A projection that returns the note ID and note content for each note.
            null,         // No "where" clause selection criteria.
            null,         // No "where" clause selection values.
            null          // Use the default sort order (modification date, descending)
        );

        if (Intent.ACTION_PASTE.equals(action)) {

            performPaste();

            mState = STATE_EDIT;
        }

        mText = (EditText) findViewById(R.id.note);

        if (savedInstanceState != null) {
            mOriginalContent = savedInstanceState.getString(ORIGINAL_CONTENT);
        }
    }

    private void showFontMenu(View view) {
        // 加载颜色菜单布局
        final View popupView = LayoutInflater.from(this).inflate(R.layout.font_menu, null);

        // 创建 PopupWindow
        final PopupWindow popupWindow = new PopupWindow(popupView,
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true);

        // 显示 PopupWindow 在屏幕底部
        popupWindow.showAtLocation(view, Gravity.BOTTOM, 0, 0);

        // 设置点击外部关闭 PopupWindow
        popupWindow.setOutsideTouchable(true);
        popupWindow.setFocusable(true);


        popupView.findViewById(R.id.noneBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                TextView textView = (TextView) findViewById(R.id.note);
                textView.setTypeface(null);
                popupView.findViewById(R.id.btn1).setVisibility(View.VISIBLE);
                popupView.findViewById(R.id.btn2).setVisibility(View.GONE);
                popupView.findViewById(R.id.btn3).setVisibility(View.GONE);
                popupView.findViewById(R.id.btn4).setVisibility(View.GONE);
            }
        });
        popupView.findViewById(R.id.loliBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                TextView textView = (TextView) findViewById(R.id.note);
                Typeface typeface = Typeface.createFromAsset(getAssets(), "font/loli.ttf");
                textView.setTypeface(typeface);
                popupView.findViewById(R.id.btn1).setVisibility(View.GONE);
                popupView.findViewById(R.id.btn2).setVisibility(View.VISIBLE);
                popupView.findViewById(R.id.btn3).setVisibility(View.GONE);
                popupView.findViewById(R.id.btn4).setVisibility(View.GONE);
            }
        });
        popupView.findViewById(R.id.qingniaoBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                TextView textView = (TextView) findViewById(R.id.note);
                Typeface typeface = Typeface.createFromAsset(getAssets(), "font/qingniao.ttf");
                textView.setTypeface(typeface);
                popupView.findViewById(R.id.btn1).setVisibility(View.GONE);
                popupView.findViewById(R.id.btn2).setVisibility(View.GONE);
                popupView.findViewById(R.id.btn3).setVisibility(View.VISIBLE);
                popupView.findViewById(R.id.btn4).setVisibility(View.GONE);
            }
        });
        popupView.findViewById(R.id.zihunBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                TextView textView = (TextView) findViewById(R.id.note);
                Typeface typeface = Typeface.createFromAsset(getAssets(), "font/zihun.ttf");
                textView.setTypeface(typeface);
                popupView.findViewById(R.id.btn1).setVisibility(View.GONE);
                popupView.findViewById(R.id.btn2).setVisibility(View.GONE);
                popupView.findViewById(R.id.btn3).setVisibility(View.GONE);
                popupView.findViewById(R.id.btn4).setVisibility(View.VISIBLE);
            }
        });

    }

    private void showSortMenu(View anchor) {
        // 加载颜色菜单布局
        final View popupView = LayoutInflater.from(this).inflate(R.layout.sort_menu, null);

        // 创建 PopupWindow
        final PopupWindow popupWindow = new PopupWindow(popupView,
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true);

        // 显示 PopupWindow 在屏幕底部
        popupWindow.showAtLocation(anchor, Gravity.BOTTOM, 0, 0);

        // 设置点击外部关闭 PopupWindow
        popupWindow.setOutsideTouchable(true);
        popupWindow.setFocusable(true);

        // 获取新建文件夹按钮
        Button newfileBtn = (Button) popupView.findViewById(R.id.newfileBtn);

        // 设置点击事件监听器
        newfileBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(NoteEditor.this);
                builder.setTitle("新建文件夹");

                // 创建一个输入框
                final EditText input = new EditText(NoteEditor.this);
                input.setHint("请输入文件夹名称");
                builder.setView(input);

                // 确保数据库和表已存在
                ensureCategoryTableExists();

                // 设置确定和取消按钮
                builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String folderName = input.getText().toString().trim();
                        if (!folderName.isEmpty()) {
                            // 检查是否已经存在该文件夹
                            Cursor cursor = getContentResolver().query(
                                    Uri.parse("content://com.google.provider.NotePad/categories"),
                                    new String[]{"name"},
                                    "name = ?",
                                    new String[]{folderName},
                                    null
                            );

                            if (cursor != null && cursor.getCount() > 0) {
                                // 如果有记录，提示名称重复
                                Toast.makeText(NoteEditor.this, "文件夹名称已存在", Toast.LENGTH_SHORT).show();
                                cursor.close();
                            } else {
                                // 如果没有记录，添加新文件夹到数据库
                                ContentValues values = new ContentValues();
                                values.put("name", folderName);
                                values.put("color", "#FFFFFF");  // 默认颜色
                                values.put("count", 0);         // 初始化计数为0

                                Uri newUri = getContentResolver().insert(
                                        Uri.parse("content://com.google.provider.NotePad/categories"), values);

                                if (newUri != null) {
                                    // 动态加载 sortfile_item 布局，显示新文件夹
                                    LinearLayout container = (LinearLayout) popupView.findViewById(R.id.itemcontainer);
                                    View newItem = LayoutInflater.from(NoteEditor.this).inflate(R.layout.sortfile_item, container, false);

                                    TextView filename = (TextView) newItem.findViewById(R.id.filename);
                                    filename.setText(folderName);

                                    container.addView(newItem);
                                    Toast.makeText(NoteEditor.this, "新建文件夹：" + folderName, Toast.LENGTH_SHORT).show();
                                }
                            }
                        } else {
                            Toast.makeText(NoteEditor.this, "文件夹名称不能为空", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                builder.setNegativeButton("取消", null);
                builder.show();
            }
        });
    }

    // 确保 category 表存在的方法
    private void ensureCategoryTableExists() {
        try {
            SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(
                    getApplicationContext().getDatabasePath("note_pad.db"), null);

            // 检查并创建 category 表
            db.execSQL("CREATE TABLE IF NOT EXISTS category ("
                    + "_id INTEGER PRIMARY KEY,"
                    + "name TEXT NOT NULL UNIQUE,"
                    + "color TEXT NOT NULL,"
                    + "count INTEGER NOT NULL DEFAULT 0"
                    + ");");
            db.close();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "数据库初始化失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void showColorMenu(View anchor) {
        // 加载颜色菜单布局
        View popupView = LayoutInflater.from(this).inflate(R.layout.color_menu, null);

        // 创建 PopupWindow
        final PopupWindow popupWindow = new PopupWindow(popupView,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true);

        // 显示 PopupWindow 在屏幕底部
        popupWindow.showAtLocation(anchor, Gravity.BOTTOM, 0, 0);

        // 设置点击外部关闭 PopupWindow
        popupWindow.setOutsideTouchable(true);
        popupWindow.setFocusable(true);

        // 获取颜色选项按钮
        final ImageButton colorWhite = (ImageButton) popupView.findViewById(R.id.colorWhite);
        final ImageButton colorOne = (ImageButton) popupView.findViewById(R.id.colorone);
        final ImageButton colorTwo = (ImageButton) popupView.findViewById(R.id.colortwo);
        final ImageButton colorThree = (ImageButton) popupView.findViewById(R.id.colorthree);
        final ImageButton colorFour = (ImageButton) popupView.findViewById(R.id.colorfour);

        // 设置颜色选项的点击事件
        colorWhite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                findViewById(R.id.note).setBackgroundColor(Color.parseColor("#FFFFFF"));
                colorWhite.setImageResource(R.drawable.chooseicon);
                colorOne.setImageResource(0);
                colorTwo.setImageResource(0);
                colorThree.setImageResource(0);
                colorFour.setImageResource(0);
            }
        });

        colorOne.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                findViewById(R.id.note).setBackgroundColor(Color.parseColor("#F1F1F1"));
                colorWhite.setImageResource(0);
                colorOne.setImageResource(R.drawable.chooseicon);
                colorTwo.setImageResource(0);
                colorThree.setImageResource(0);
                colorFour.setImageResource(0);
            }
        });

        colorTwo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                findViewById(R.id.note).setBackgroundColor(Color.parseColor("#F5F1E8"));
                colorWhite.setImageResource(0);
                colorOne.setImageResource(0);
                colorTwo.setImageResource(R.drawable.chooseicon);
                colorThree.setImageResource(0);
                colorFour.setImageResource(0);
            }
        });

        colorThree.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                findViewById(R.id.note).setBackgroundColor(Color.parseColor("#E9E4D8"));
                colorWhite.setImageResource(0);
                colorOne.setImageResource(0);
                colorTwo.setImageResource(0);
                colorThree.setImageResource(R.drawable.chooseicon);
                colorFour.setImageResource(0);
            }
        });

        colorFour.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                findViewById(R.id.note).setBackgroundColor(Color.parseColor("#DCE9DD"));
                colorWhite.setImageResource(0);
                colorOne.setImageResource(0);
                colorTwo.setImageResource(0);
                colorThree.setImageResource(0);
                colorFour.setImageResource(R.drawable.chooseicon);
            }
        });

        // 显示 PopupWindow
        popupWindow.showAsDropDown(anchor, 0, 10, Gravity.END);
    }

    private void showChooseMenu(View anchor) {
        // 加载菜单布局
        View popupView = LayoutInflater.from(this).inflate(R.layout.popup_menu, null);

        // 创建 PopupWindow
        final PopupWindow popupWindow = new PopupWindow(popupView,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true);

        // 设置点击外部关闭 PopupWindow
        popupWindow.setOutsideTouchable(true);
        popupWindow.setFocusable(true);

        // 找到菜单项并设置点击事件
        TextView menuItem1 = (TextView) popupView.findViewById(R.id.deleteBtn);
        TextView menuItem2 = (TextView) popupView.findViewById(R.id.exportBtn);

        menuItem1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                deleteNote();
                popupWindow.dismiss(); // 点击后关闭菜单
                finish();
            }
        });

        menuItem2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                export();
                popupWindow.dismiss(); // 点击后关闭菜单
            }
        });

        // 显示 PopupWindow，位置为按钮下方
        popupWindow.showAsDropDown(anchor, 0, 10, Gravity.END);
    }

    private static final int REQUEST_CODE_EXPORT = 100; // 100 是一个随意选定的


    private void export() {
        // 创建一个输入框
        final EditText input = new EditText(this);
        input.setHint("请输入文件名");

        // 弹出对话框
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("导出笔记")
                .setView(input)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        String fileName = input.getText().toString().trim();
                        if (fileName.isEmpty()) {
                            Toast.makeText(NoteEditor.this, "文件名不能为空", Toast.LENGTH_SHORT).show();
                        } else {
                            // 启动文件选择器
                            openFilePicker(fileName);
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.BLACK);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_EXPORT && resultCode == RESULT_OK) {
            Uri fileUri = data.getData();

            if (fileUri != null) {
                saveNoteToFile(fileUri);
            } else {
                Toast.makeText(this, "文件创建失败", Toast.LENGTH_SHORT).show();
            }
        }
    }
    private void saveNoteToFile(Uri fileUri) {
        try {
            // 获取笔记内容
            String noteContent = mText.getText().toString();

            // 打开输出流并写入数据
            try (OutputStream outputStream = getContentResolver().openOutputStream(fileUri)) {
                if (outputStream != null) {
                    outputStream.write(noteContent.getBytes());
                    outputStream.flush();
                    Toast.makeText(this, "笔记导出成功", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "无法打开文件", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "导出失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openFilePicker(String fileName) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, fileName + ".txt"); // 用户输入的文件名
        startActivityForResult(intent, REQUEST_CODE_EXPORT);
    }


    @Override
    protected void onResume() {
        super.onResume();

        if (mCursor != null) {

            mCursor.requery();

            mCursor.moveToFirst();

            if (mState == STATE_EDIT) {

                int colTitleIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
                String title = mCursor.getString(colTitleIndex);
                Resources res = getResources();
                String text = String.format(res.getString(R.string.title_edit), title);
                setTitle(text);

            } else if (mState == STATE_INSERT) {
                setTitle(getText(R.string.title_create));
            }

            int colNoteIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
            String note = mCursor.getString(colNoteIndex);
            mText.setTextKeepState(note);

            // Stores the original note text, to allow the user to revert changes.
            if (mOriginalContent == null) {
                mOriginalContent = note;
            }

        } else {
            setTitle(getText(R.string.error_title));
            mText.setText(getText(R.string.error_message));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // Save away the original text, so we still have it if the activity
        // needs to be killed while paused.
        outState.putString(ORIGINAL_CONTENT, mOriginalContent);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mCursor != null) {

            // Get the current note text.
            String text = mText.getText().toString();
            int length = text.length();

            if (isFinishing() && (length == 0)) {
                setResult(RESULT_CANCELED);
                deleteNote();

            } else if (mState == STATE_EDIT) {
                // Creates a map to contain the new values for the columns
                updateNote(text, null);
            } else if (mState == STATE_INSERT) {
                updateNote(text, text);
                mState = STATE_EDIT;
          }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.editor_options_menu, menu);

        if (mState == STATE_EDIT) {

            Intent intent = new Intent(null, mUri);
            intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
            menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                    new ComponentName(this, NoteEditor.class), null, intent, 0, null);
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // Check if note has changed and enable/disable the revert option
        int colNoteIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
        String savedNote = mCursor.getString(colNoteIndex);
        String currentNote = mText.getText().toString();
        if (savedNote.equals(currentNote)) {
            menu.findItem(R.id.menu_revert).setVisible(false);
        } else {
            menu.findItem(R.id.menu_revert).setVisible(true);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle all of the possible menu actions.
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                break;
            case R.id.menu_save:
                String text = mText.getText().toString();
                updateNote(text, null);
                finish();
                break;
            case R.id.menu_delete:
                deleteNote();
                finish();
                break;
            case R.id.menu_revert:
                cancelNote();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private final void performPaste() {

        ClipboardManager clipboard = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);

        ContentResolver cr = getContentResolver();

        ClipData clip = clipboard.getPrimaryClip();
        if (clip != null) {

            String text=null;
            String title=null;

            ClipData.Item item = clip.getItemAt(0);

            Uri uri = item.getUri();

            if (uri != null && NotePad.Notes.CONTENT_ITEM_TYPE.equals(cr.getType(uri))) {

                Cursor orig = cr.query(
                        uri,            // URI for the content provider
                        PROJECTION,     // Get the columns referred to in the projection
                        null,           // No selection variables
                        null,           // No selection variables, so no criteria are needed
                        null            // Use the default sort order
                );

                if (orig != null) {
                    if (orig.moveToFirst()) {
                        int colNoteIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
                        int colTitleIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
                        text = orig.getString(colNoteIndex);
                        title = orig.getString(colTitleIndex);
                    }

                    // Closes the cursor.
                    orig.close();
                }
            }

            if (text == null) {
                text = item.coerceToText(this).toString();
            }

            updateNote(text, title);
        }
    }

    /**
     * Replaces the current note contents with the text and title provided as arguments.
     * @param text The new note contents to use.
     * @param title The new note title to use
     */
    private final void updateNote(String text, String title) {
        // 获取 R.id.title1 的内容作为标题
        TextView titleView = (TextView) findViewById(R.id.title1);
        title = titleView.getText().toString(); // 获取标题内容

        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, System.currentTimeMillis());

        if (mState == STATE_INSERT) {

            if (title == null || title.isEmpty()) {

                int length = text.length();

                title = text.substring(0, Math.min(30, length));

                if (length > 30) {
                    int lastSpace = title.lastIndexOf(' ');
                    if (lastSpace > 0) {
                        title = title.substring(0, lastSpace);
                    }
                }
            }
            values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
        } else if (title != null) {
            values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
        }
        values.put(NotePad.Notes.COLUMN_NAME_NOTE, text);

        getContentResolver().update(
                mUri,    // The URI for the record to update.
                values,  // The map of column names and new values to apply to them.
                null,    // No selection criteria are used, so no where columns are necessary.
                null     // No where columns are used, so no where arguments are necessary.
            );


    }

    private final void cancelNote() {
        if (mCursor != null) {
            if (mState == STATE_EDIT) {
                // Put the original note text back into the database
                mCursor.close();
                mCursor = null;
                ContentValues values = new ContentValues();
                values.put(NotePad.Notes.COLUMN_NAME_NOTE, mOriginalContent);
                getContentResolver().update(mUri, values, null, null);
            } else if (mState == STATE_INSERT) {
                // We inserted an empty note, make sure to delete it
                deleteNote();
            }
        }
        setResult(RESULT_CANCELED);
        finish();
    }

    /**
     * Take care of deleting a note.  Simply deletes the entry.
     */
    private final void deleteNote() {
        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
            getContentResolver().delete(mUri, null, null);
            mText.setText("");
        }
    }
}
