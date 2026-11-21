package com.livecraft

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*

class GiftRulesActivity : Activity() {
    private val rules = mutableListOf<GiftRule>()
    private lateinit var list: LinearLayout
    private val types = GiftRuleStore.ACTION_TYPES
    private val labels get() = types.map { GiftRuleStore.actionLabel(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rules.addAll(GiftRuleStore.load(this))
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 24, 20, 20); setBackgroundColor(Color.BLACK) }
        root.addView(label("PRESENTES E AÇÕES", 24f, Color.WHITE, true))
        root.addView(label("Escolha por nome. Não precisa decorar IDs.", 14f, 0xFFCCCCCC.toInt()))
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
        rules.forEachIndexed { i, rule ->
            val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12, 10, 12, 10); background = rounded(16, Color.BLACK, Color.WHITE) }
            card.addView(label(rule.icon + "  " + rule.giftName, 17f, Color.WHITE, true))
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
        val name = EditText(this).apply { hint = "Nome exato do presente na LIVE"; setSingleLine(); setText(old?.giftName.orEmpty()) }
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
        form.addView(label("NOME DO PRESENTE", 12f, Color.DKGRAY, true)); form.addView(name)
        form.addView(label("ÍCONE", 12f, Color.DKGRAY, true)); form.addView(icon)
        form.addView(label("AÇÃO", 12f, Color.DKGRAY, true)); form.addView(action)
        form.addView(label("ESCOLHA UMA OPÇÃO", 12f, Color.DKGRAY, true)); form.addView(choice); form.addView(amount)
        form.addView(label("Mobs, itens, efeitos e comandos aparecem por nome. Os IDs ficam escondidos.", 12f, Color.DKGRAY))
        AlertDialog.Builder(this).setTitle(if (old == null) "Novo presente" else "Editar presente").setView(form)
            .setNegativeButton("Cancelar", null).setPositiveButton("SALVAR") { _, _ ->
                val gift = name.text.toString().trim()
                val type = types[action.selectedItemPosition]
                val opts = options(type)
                val value = if (opts.isNotEmpty()) opts.getOrNull(choice.selectedItemPosition)?.second.orEmpty() else ""
                if (gift.isBlank()) { Toast.makeText(this, "Digite o nome do presente.", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                if (type in listOf("SUMMON", "GIVE", "EFFECT", "COMMAND") && value.isBlank()) { Toast.makeText(this, "Escolha uma opção.", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                val selectedIcon = GiftRuleStore.iconOptions.getOrElse(icon.selectedItemPosition) { "🎁" }
                val rule = GiftRule(gift, type, value, amount.text.toString().toIntOrNull()?.coerceIn(1, 64) ?: 1, selectedIcon)
                var target = old?.let { rules.indexOfFirst { r -> r.giftName.equals(it.giftName, true) } } ?: -1
                val duplicate = rules.indexOfFirst { it.giftName.equals(gift, true) }
                if (duplicate >= 0 && duplicate != target) { rules.removeAt(duplicate); if (target > duplicate) target-- }
                if (target < 0) rules.add(rule) else rules[target] = rule
                save()
            }.show()
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
    private fun save() { GiftRuleStore.save(this, rules); render() }
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
