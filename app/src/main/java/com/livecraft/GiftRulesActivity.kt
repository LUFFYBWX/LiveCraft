package com.livecraft

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
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
        root.addView(label("Cadastre presentes novos ou altere o que cada um faz no Minecraft.", 14f, 0xFFCCCCCC.toInt()))
        root.addView(space(12))
        root.addView(button("+ ADICIONAR PRESENTE") { edit(null) })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(button("VOLTAR") { finish() })
        setContentView(root)
        render()
    }

    private fun render() {
        list.removeAllViews()
        if (rules.isEmpty()) list.addView(label("Nenhum presente configurado.", 14f, 0xFFBBBBBB.toInt()))
        rules.forEachIndexed { i, rule ->
            val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12, 10, 12, 10); background = rounded(16, Color.BLACK, Color.WHITE) }
            card.addView(label(rule.giftName, 17f, Color.WHITE, true))
            val detail = GiftRuleStore.actionLabel(rule.actionType) + if (rule.value.isNotBlank()) " • ${rule.value}" else ""
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
        val name = EditText(this).apply { hint = "Nome exato do presente no TikTok"; setSingleLine(); setText(old?.giftName.orEmpty()) }
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@GiftRulesActivity, android.R.layout.simple_spinner_dropdown_item, labels)
            setSelection((old?.actionType?.let { types.indexOf(it) } ?: 0).coerceAtLeast(0))
        }
        val value = EditText(this).apply { hint = "ID da entidade, item ou efeito"; setSingleLine(); setText(old?.value.orEmpty()) }
        val amount = EditText(this).apply { hint = "Quantidade/nível (1 a 64)"; inputType = InputType.TYPE_CLASS_NUMBER; setSingleLine(); setText((old?.amount ?: 1).toString()) }
        form.addView(label("NOME DO PRESENTE", 12f, Color.DKGRAY, true)); form.addView(name)
        form.addView(label("AÇÃO", 12f, Color.DKGRAY, true)); form.addView(spinner)
        form.addView(label("PARÂMETRO (se necessário)", 12f, Color.DKGRAY, true)); form.addView(value); form.addView(amount)
        form.addView(label("Exemplos: zombie, diamond, speed. A quantidade vale para itens e nível para efeitos.", 12f, Color.DKGRAY))
        AlertDialog.Builder(this).setTitle(if (old == null) "Novo presente" else "Editar presente").setView(form)
            .setNegativeButton("Cancelar", null).setPositiveButton("SALVAR") { _, _ ->
                val gift = name.text.toString().trim()
                val type = types[spinner.selectedItemPosition]
                val parameter = value.text.toString().trim()
                if (gift.isBlank()) { Toast.makeText(this, "Digite o nome do presente.", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                if (type in listOf("SUMMON", "GIVE", "EFFECT") && !parameter.matches(Regex("[a-zA-Z0-9_.:-]{1,64}"))) {
                    Toast.makeText(this, "Informe um ID válido para essa ação.", Toast.LENGTH_SHORT).show(); return@setPositiveButton
                }
                val rule = GiftRule(gift, type, parameter, amount.text.toString().toIntOrNull()?.coerceIn(1, 64) ?: 1)
                val duplicate = rules.indexOfFirst { it.giftName.equals(gift, true) }
                if (duplicate >= 0 && duplicate != index) rules.removeAt(duplicate)
                if (index == null) rules.add(rule) else rules[index.coerceAtMost(rules.lastIndex)] = rule
                save()
            }.show()
    }

    private fun remove(index: Int) {
        AlertDialog.Builder(this).setMessage("Remover a regra de ${rules[index].giftName}?")
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
    private fun space(height: Int): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, height) }
    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value; isAllCaps = false; setTextColor(Color.WHITE); background = rounded(22, Color.BLACK, Color.WHITE)
        setPadding(10, 8, 10, 8); val p = LinearLayout.LayoutParams(-1, -2); p.bottomMargin = 8; layoutParams = p
        setOnClickListener { action() }
    }
}
