package com.livecraft

import android.app.Activity
import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL

class GiftRulesActivity : Activity() {
    private val rules = mutableListOf<GiftRule>()
    private lateinit var list: LinearLayout
    private val types = GiftRuleStore.ACTION_TYPES
    private val labels get() = types.map { GiftRuleStore.actionLabel(it) }
    private val catalog get() = GiftRuleStore.giftCatalog(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rules.addAll(GiftRuleStore.load(this))
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 24, 20, 20); setBackgroundColor(Color.BLACK) }
        root.addView(label("PRESENTES E AÇÕES", 24f, Color.WHITE, true))
        root.addView(label("Biblioteca com presentes conhecidos e ícones reais. Presentes novos entram quando aparecem na LIVE.", 14f, 0xFFCCCCCC.toInt()))
        root.addView(button("+ ADICIONAR PRESENTE") { edit(null) })
        root.addView(button("RESTAURAR PRESENTES PADRÃO") { restoreDefaults() })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(button("VOLTAR") { finish() })
        setContentView(root)
        render()
    }

    private fun options(type: String): List<Pair<String, String>> = when (type) {
        "SUMMON" -> GiftRuleStore.mobs
        "GIVE" -> GiftRuleStore.items
        "EFFECT" -> GiftRuleStore.effects
        "COMMAND" -> GiftRuleStore.commands
        else -> emptyList()
    }

    private fun display(type: String, value: String): String = options(type).firstOrNull { it.second == value }?.first ?: value

    private fun render() {
        list.removeAllViews()
        list.addView(label("BIBLIOTECA: " + catalog.size + " PRESENTES", 13f, Color.WHITE, true))
        rules.forEachIndexed { i, rule ->
            val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12, 10, 12, 10); background = rounded(16, Color.BLACK, Color.WHITE) }
            val giftRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
            val giftImageUrl = rule.imageUrl.ifBlank { catalog.firstOrNull { it.name.equals(rule.giftName, true) }?.imageUrl.orEmpty() }
            if (giftImageUrl.isNotBlank()) {
                val image = ImageView(this).apply { layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)); scaleType = ImageView.ScaleType.FIT_CENTER; tag = giftImageUrl }
                loadGiftImage(giftImageUrl, image)
                giftRow.addView(image)
            } else {
                giftRow.addView(label(rule.icon, 30f, Color.WHITE))
            }
            giftRow.addView(label(rule.giftName, 17f, Color.WHITE, true).apply { setPadding(10, 5, 0, 5) })
            card.addView(giftRow)
            val detail = GiftRuleStore.actionLabel(rule.actionType) + if (rule.value.isNotBlank()) " • " + display(rule.actionType, rule.value) else ""
            card.addView(label(detail, 13f, 0xFFBBBBBB.toInt()))
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(button("EDITAR") { edit(i) }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(button("REMOVER") { remove(i) }, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(row)
            val p = LinearLayout.LayoutParams(-1, -2); p.bottomMargin = 10
            list.addView(card, p)
        }
    }

    private fun edit(index: Int?) {
        val old = index?.let { rules[it] }
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 6, 18, 0) }
        val entries = catalog
        val name = AutoCompleteTextView(this).apply {
            hint = "Selecione ou digite o nome exato"
            setSingleLine()
            setText(old?.giftName.orEmpty())
            threshold = 1
            setAdapter(ArrayAdapter(this@GiftRulesActivity, android.R.layout.simple_dropdown_item_1line, entries.map { it.name }))
        }
        val preview = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64))
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0xFF171717.toInt())
        }
        var selectedImageUrl = old?.imageUrl.orEmpty().ifBlank { entries.firstOrNull { it.name.equals(old?.giftName, true) }?.imageUrl.orEmpty() }
        if (selectedImageUrl.isNotBlank()) loadGiftImage(selectedImageUrl, preview)
        fun selectGift(giftName: String) {
            val entry = entries.firstOrNull { it.name.equals(giftName.trim(), true) }
            selectedImageUrl = entry?.imageUrl.orEmpty()
            preview.setImageDrawable(null)
            if (selectedImageUrl.isNotBlank()) loadGiftImage(selectedImageUrl, preview)
        }
        name.setOnItemClickListener { _, _, position, _ -> selectGift(entries.firstOrNull { it.name.equals(name.text.toString(), true) }?.name ?: name.text.toString()) }
        val chooseGift = button("ABRIR BIBLIOTECA DE PRESENTES") {
            openGiftLibrary(entries) { entry ->
                name.setText(entry.name)
                selectGift(entry.name)
            }
        }
        val icon = Spinner(this).apply {
            adapter = ArrayAdapter(this@GiftRulesActivity, android.R.layout.simple_spinner_dropdown_item, GiftRuleStore.iconOptions)
            setSelection(GiftRuleStore.iconOptions.indexOf(old?.icon ?: "🎁").coerceAtLeast(0))
        }
        val action = Spinner(this).apply {
            adapter = ArrayAdapter(this@GiftRulesActivity, android.R.layout.simple_spinner_dropdown_item, labels)
            setSelection((old?.actionType?.let { types.indexOf(it) } ?: 0).coerceAtLeast(0))
        }
        val choice = Spinner(this)
        val amount = EditText(this).apply { hint = "Quantidade/nível (1 a 64)"; inputType = InputType.TYPE_CLASS_NUMBER; setSingleLine(); setText((old?.amount ?: 1).toString()) }
        fun refresh(selected: String?) {
            val opts = options(types[action.selectedItemPosition])
            choice.adapter = ArrayAdapter(this@GiftRulesActivity, android.R.layout.simple_spinner_dropdown_item, opts.map { it.first })
            choice.setSelection(opts.indexOfFirst { it.second == selected }.coerceAtLeast(0))
            choice.isEnabled = opts.isNotEmpty()
            amount.visibility = if (types[action.selectedItemPosition] == "GIVE" || types[action.selectedItemPosition] == "EFFECT") View.VISIBLE else View.GONE
        }
        action.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { refresh(if (old?.actionType == types[position]) old.value else null) }
        }
        form.addView(label("PRESENTE", 12f, Color.DKGRAY, true))
        form.addView(name)
        form.addView(chooseGift)
        form.addView(preview)
        form.addView(label("ÍCONE ALTERNATIVO (se não houver imagem)", 12f, Color.DKGRAY, true))
        form.addView(icon)
        form.addView(label("AÇÃO", 12f, Color.DKGRAY, true))
        form.addView(action)
        form.addView(label("ESCOLHA UMA OPÇÃO", 12f, Color.DKGRAY, true))
        form.addView(choice)
        form.addView(amount)
        form.addView(label("Os ícones oficiais são guardados automaticamente quando o presente aparece na LIVE.", 12f, Color.DKGRAY))
        AlertDialog.Builder(this).setTitle(if (old == null) "Novo presente" else "Editar presente").setView(form)
            .setNegativeButton("Cancelar", null).setPositiveButton("SALVAR") { _, _ ->
                val gift = name.text.toString().trim()
                val type = types[action.selectedItemPosition]
                val opts = options(type)
                val value = if (opts.isNotEmpty()) opts.getOrNull(choice.selectedItemPosition)?.second.orEmpty() else ""
                if (gift.isBlank()) { Toast.makeText(this, "Escolha ou digite o nome do presente.", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                if (type in listOf("SUMMON", "GIVE", "EFFECT", "COMMAND") && value.isBlank()) { Toast.makeText(this, "Escolha uma opção.", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                val selectedIcon = GiftRuleStore.iconOptions.getOrElse(icon.selectedItemPosition) { "🎁" }
                val savedImage = selectedImageUrl.ifBlank { entries.firstOrNull { it.name.equals(gift, true) }?.imageUrl.orEmpty() }
                val rule = GiftRule(gift, type, value, amount.text.toString().toIntOrNull()?.coerceIn(1, 64) ?: 1, selectedIcon, savedImage)
                var target = old?.let { rules.indexOfFirst { r -> r.giftName.equals(it.giftName, true) } } ?: -1
                val duplicate = rules.indexOfFirst { it.giftName.equals(gift, true) }
                if (duplicate >= 0 && duplicate != target) { rules.removeAt(duplicate); if (target > duplicate) target-- }
                if (target < 0) rules.add(rule) else rules[target] = rule
                save()
            }.show()
    }

    private fun openGiftLibrary(entries: List<GiftCatalogEntry>, onSelect: (GiftCatalogEntry) -> Unit) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(10))
            setBackgroundColor(0xFF101010.toInt())
        }
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val headingText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        headingText.addView(TextView(this).apply {
            text = "Escolha um presente"
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        })
        val count = TextView(this).apply {
            text = entries.size.toString() + " presentes disponíveis"
            textSize = 12f
            setTextColor(0xFFAAAAAA.toInt())
            setPadding(0, dp(3), 0, 0)
        }
        headingText.addView(count)
        heading.addView(headingText, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(heading)

        val search = EditText(this).apply {
            hint = "Pesquisar presente..."
            setSingleLine(true)
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF999999.toInt())
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            background = rounded(14, 0xFF242424.toInt(), 0xFF555555.toInt())
            setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_search, 0, 0, 0)
            compoundDrawablePadding = dp(8)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val searchParams = LinearLayout.LayoutParams(-1, dp(48))
        searchParams.topMargin = dp(14)
        searchParams.bottomMargin = dp(10)
        content.addView(search, searchParams)

        val scroll = ScrollView(this)
        val grid = android.widget.GridLayout(this).apply {
            columnCount = 4
            useDefaultMargins = false
        }
        val empty = TextView(this).apply {
            text = "Nenhum presente encontrado"
            textSize = 14f
            setTextColor(0xFFBBBBBB.toInt())
            gravity = android.view.Gravity.CENTER
            visibility = View.GONE
            setPadding(0, dp(36), 0, dp(36))
        }
        fun renderGrid(query: String) {
            grid.removeAllViews()
            val filtered = entries.filter { it.name.contains(query.trim(), ignoreCase = true) }
            count.text = if (query.isBlank()) "${entries.size} presentes disponíveis" else "${filtered.size} resultado(s)"
            empty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
            filtered.forEach { entry ->
                val tile = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = android.view.Gravity.CENTER
                    setPadding(dp(4), dp(9), dp(4), dp(8))
                    isClickable = true
                    isFocusable = true
                    background = rounded(14, 0xFF202020.toInt(), 0xFF484848.toInt())
                }
                val image = ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(58), dp(58))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = entry.name
                }
                if (entry.imageUrl.isNotBlank()) loadGiftImage(entry.imageUrl, image)
                else image.setImageResource(android.R.drawable.ic_menu_gallery)
                tile.addView(image)
                tile.addView(TextView(this).apply {
                    text = entry.name
                    textSize = 11f
                    setTextColor(Color.WHITE)
                    gravity = android.view.Gravity.CENTER
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(0, dp(5), 0, 0)
                })
                tile.addView(TextView(this).apply {
                    text = "◆ " + entry.coins
                    textSize = 10f
                    setTextColor(0xFFFFD45C.toInt())
                    gravity = android.view.Gravity.CENTER
                    setPadding(0, dp(3), 0, 0)
                })
                tile.setOnClickListener { onSelect(entry); dialog.dismiss() }
                val params = android.widget.GridLayout.LayoutParams().apply {
                    width = 0
                    height = -2
                    columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                }
                grid.addView(tile, params)
            }
        }
        scroll.addView(grid)
        val body = FrameLayout(this).apply {
            addView(scroll, FrameLayout.LayoutParams(-1, -1))
            addView(empty, FrameLayout.LayoutParams(-1, -2, android.view.Gravity.CENTER))
        }
        content.addView(body, LinearLayout.LayoutParams(-1, dp(430)))
        val dialog = AlertDialog.Builder(this).setView(content)
            .setNegativeButton("FECHAR", null).create()
        // Grid is rebuilt as the user types; the dialog closes after a selection.
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderGrid(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        renderGrid("")
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.96f).toInt(), -2)
    }

    private fun loadGiftImage(url: String, target: ImageView) {
        if (!url.startsWith("https://")) return
        target.tag = url
        Thread {
            val bitmap = try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.instanceFollowRedirects = true
                connection.inputStream.use { BitmapFactory.decodeStream(it) }.also { connection.disconnect() }
            } catch (_: Exception) { null }
            if (bitmap != null) runOnUiThread {
                if (target.tag == url) target.setImageBitmap(bitmap)
            }
        }.start()
    }

    private fun restoreDefaults() {
        AlertDialog.Builder(this).setMessage("Adicionar novamente os presentes padrão? Presentes personalizados serão mantidos.")
            .setNegativeButton("Cancelar", null).setPositiveButton("ADICIONAR") { _, _ ->
                GiftRuleStore.defaults().forEach { preset ->
                    val at = rules.indexOfFirst { it.giftName.equals(preset.giftName, true) }
                    if (at < 0) rules.add(preset) else rules[at] = preset
                }
                save()
            }.show()
    }

    private fun remove(index: Int) {
        AlertDialog.Builder(this).setMessage("Remover a regra de " + rules[index].giftName + "?")
            .setNegativeButton("Cancelar", null).setPositiveButton("REMOVER") { _, _ -> rules.removeAt(index); save() }.show()
    }

    private fun save() {
        GiftRuleStore.save(this, rules)
        render()
        if (getSharedPreferences("livecraft", MODE_PRIVATE).getBoolean(FloatingOverlayService.KEY_PANEL, false)) {
            val intent = android.content.Intent(this, FloatingOverlayService::class.java)
                .setAction(FloatingOverlayService.ACTION_REFRESH)
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun rounded(radius: Int, fill: Int, stroke: Int) = GradientDrawable().apply {
        setColor(fill); cornerRadius = radius * resources.displayMetrics.density
        setStroke(resources.displayMetrics.density.toInt().coerceAtLeast(1), stroke)
    }
    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD; setPadding(0, 5, 0, 5)
    }
    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value; isAllCaps = false; setTextColor(Color.WHITE); background = rounded(22, Color.BLACK, Color.WHITE)
        setPadding(10, 8, 10, 8); val p = LinearLayout.LayoutParams(-1, -2); p.bottomMargin = 8; layoutParams = p
        setOnClickListener { action() }
    }
}
