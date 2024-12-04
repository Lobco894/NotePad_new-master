# NotepadMaster - 安卓笔记应用

NotepadMaster 是一款基于 Google Notepad Master 开发的安卓笔记应用，旨在帮助用户高效管理个人笔记。应用主要提供了笔记的基本管理功能，包括记录笔记时间戳、搜索功能、笔记分类和标签管理等，便于用户查看历史记录并快速定位需要的信息。

## 功能模块

### 基础功能

#### 笔记显示时间戳
每个笔记在创建或编辑时都会自动记录时间，并在笔记列表中展示，帮助用户追踪笔记的创建和更新信息。

#### 搜索笔记功能
提供搜索功能，支持根据标题进行笔记的搜索，方便快捷。

### 附加功能

#### 笔记 UI 美化
替换原始 UI，采用简洁清晰的搭配方式，让人有更好的交互体验。

#### 笔记导出功能
用户可以选择导出笔记到本地，便于备份和使用。

#### 偏好设置
可以在主界面选择背景设置，根据个人喜好选择各种合适的背景。在笔记编辑界面可以自定义背景的颜色，以及笔记的字体样式。

#### 字数显示
可以随时显示正在编辑笔记的总字数。

## 功能实现

### 笔记显示时间戳
在`notelist_item.xml`中添加时间戳的显示位置

```
<TextView
            android:id="@+id/timetext"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="bottom"
            android:background="#D8D8D8"
            android:gravity="right"
            android:text="timecurrent"
            android:textColor="#6F6F6F"
            tools:ignore="MissingConstraints" />
```

在`NotesList.java`中完成时间戳的显示，要求：在数据库查询时，新增对修改时间字段的读取，确保获取每条笔记的时间戳信息。

添加查询投影
在 PROJECTION 数组中加入时间戳字段：

```
private static final String[] PROJECTION = new String[] {
         NotePad.Notes._ID, // 0
         NotePad.Notes.COLUMN_NAME_TITLE, // 1
         NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
         NotePad.Notes.COLUMN_NAME_NOTE
 };
```

获取时间戳并格式化
在适当的位置添加以下代码：

```
    long timestamp = cursor.getLong(cursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE));
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    String formattedDate = dateFormat.format(new Date(timestamp));
    TextView timeTextView = (TextView) view.findViewById(R.id.time_text);
    timeTextView.setText(formattedDate);
```

右下角显示笔记更新时间，并且根据更新时间来对笔记列表进行排列，便于用户方便快捷地了解笔记的相关信息。

![](./001.png)

### 搜索笔记功能

首先在`home.xml`中添加搜索框的显示，我采用了一个`imagebutton`作为搜索按钮，点击以后显示`edittext`作为搜索框

搜索按钮

```
        <ImageButton
            android:id="@+id/searchbtn"
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_gravity="center"
            android:layout_weight="1"
            android:contentDescription="@null"
            android:background="@android:color/transparent"
            android:scaleType="centerInside"
            android:src="@drawable/search_gray" />
```

搜索框，通过`android:visibility="gone"`初始设置为隐藏

```
        <EditText
            android:id="@+id/search"
            android:layout_width="350dp"
            android:layout_height="match_parent"
            android:layout_weight="1"
            android:hint="请输入搜索内容"
            android:padding="10dp"
            android:visibility="gone" />
```

再添加一个搜索框返回的按钮

```
        <ImageButton
            android:id="@+id/searchBackBtn"
            android:layout_width="30dp"
            android:layout_height="wrap_content"
            android:layout_gravity="center"
            android:layout_weight="1"
            android:contentDescription="@null"
            android:background="@android:color/transparent"
            android:scaleType="centerInside"
            android:src="@drawable/back"
            android:visibility="gone" />
```

在`NotesList.java`添加组件的初始化，每个组件的功能实现都需要显初始化，类似如此，之后不再赘述

```
        final ImageButton searchButton = (ImageButton) findViewById(R.id.searchbtn);
        final TextView searchEditText = (TextView) findViewById(R.id.search);
        final ImageButton closeSearchButton = (ImageButton) findViewById(R.id.searchBackBtn);
```

点击搜索按钮，显示输入框

```
        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchEditText.setVisibility(View.VISIBLE);  // 显示输入框
                closeSearchButton.setVisibility(View.VISIBLE);
                findViewById(R.id.title).setVisibility(View.GONE);
                anb.setVisibility(View.GONE);
                searchButton.setVisibility(View.GONE);     // 隐藏搜索按钮
            }
        });
```

点击关闭按钮，隐藏输入框并恢复搜索按钮

```
closeSearchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchEditText.setVisibility(View.GONE);  // 隐藏输入框
                closeSearchButton.setVisibility(View.GONE);
                findViewById(R.id.title).setVisibility(View.VISIBLE);
                anb.setVisibility(View.VISIBLE);
                searchButton.setVisibility(View.VISIBLE);  // 恢复搜索按钮
            }
        });
```

设置输入框内容变化时进行搜索

```
searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                // 当输入框内容发生变化时，进行搜索
                String query = searchEditText.getText().toString();
                performSearch(query);  // 调用搜索功能
            }

            @Override
            public void afterTextChanged(Editable editable) {}
        });
```

搜索功能实现

```
    private void performSearch(String query) {
        Cursor cursor = managedQuery(
                NotePad.Notes.CONTENT_URI,
                PROJECTION,
                NotePad.Notes.COLUMN_NAME_TITLE + " LIKE ?",
                new String[]{"%" + query + "%"},
                NotePad.Notes.DEFAULT_SORT_ORDER
        );

        SimpleCursorAdapter adapter = (SimpleCursorAdapter) getListAdapter();
        adapter.changeCursor(cursor);
    }
```


点击搜索按钮，跳出搜索框，根据输入在内容中模糊查找相应的笔记。

![](./002.png)
![](./003.png)
![](./004.png)

### 笔记 UI 美化
去除了原始界面的交互组件，更改为更加美观的外表。

**原始 UI**

![](./005.png)  

**美化后 UI**

![](./006.png)

### 笔记导出功能
在笔记编辑界面的选项中，可以选择笔记导出功能。

![](./007.png)
![](./008.png)
![](./009.png)

输入文件名后可以选择保存的位置。如果输入为空会出现提示，保存成功后也有提示。

![](./010.png)
![](./011.png)
![](./012.png)

### 偏好设置
点击右上角选项功能，可以出现多种背景风格。

![](./013.png)
![](./014.png)
![](./015.png)
![](./016.png)

在笔记编辑界面可以选择字体样式。

![](./017.png)
![](./018.png)
![](./019.png)
![](./020.png)

在笔记编辑界面还可以选择背景颜色。

![](./021.png)
![](./022.png)
![](./023.png)

### 字数显示
实时显示该笔记的总字数。

![](./024.png)

