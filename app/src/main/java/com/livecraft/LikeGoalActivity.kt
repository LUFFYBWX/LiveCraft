package com.livecraft

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*

class LikeGoalActivity : Activity() {
    private data class GoalAction(val label: String, val type: String, val value: String)
    private val actions by lazy {
        buildList {
            add(GoalAction("🧟 Invocar zumbi", "COMMAND", "spawn_zombie"))
            addAll(GiftRuleStore.commands.map { GoalAction(it.first, "COMMAND", it.second) })
            addAll(GiftRuleStore.mobs.map { GoalAction("Invocar: " + it.first, "SUMMON", it.second) })
            addAll(GiftRuleStore.items.map { GoalAction("Dar item: " + it.first, "GIVE", it.second) })
            addAll(GiftRuleStore.effects.map { GoalAction("Efeito: " + it.first, "EFFECT", it.second) })
            add(GoalAction("⌨️ Comando personalizado", "CUSTOM", ""))
        }.distinctBy { it.type + ":" + it.value }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("livecraft", MODE_PRIVATE)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(24), dp(20), dp(20)); setBackgroundColor(Color.BLACK) }
        root.addView(label("META DE LIKES", 24f, Color.WHITE, true))
        root.addView(label("Configure no mesmo formato dos presentes: quantidade ❤️ = ação.", 14f, 0xFFCCCCCC.toInt()))
        root.addView(label("QUANTIDADE DE LIKES", 13f, Color.WHITE, true))
        val target = input("Ex.: 100", prefs.getInt(GameBridgeService.KEY_LIKE_GOAL_TARGET, GameBridgeService.DEFAULT_LIKE_GOAL_TARGET).toString(), android.text.InputType.TYPE_CLASS_NUMBER)
        root.addView(target)
        root.addView(label("AÇÃO QUANDO A META FOR ATINGIDA", 13f, Color.WHITE, true))
        val action = Spinner(this).apply {
            adapter = ArrayAdapter(this@LikeGoalActivity, android.R.layout.simple_spinner_dropdown_item, actions.map { it.label })
            val type = prefs.getString(GameBridgeService.KEY_LIKE_GOAL_ACTION_TYPE, "COMMAND")
            val value = prefs.getString(GameBridgeService.KEY_LIKE_GOAL_ACTION_VALUE, "spawn_zombie")
            setSelection(actions.indexOfFirst { it.type == type && it.value == value }.coerceAtLeast(0))
        }
        root.addView(action)
        val amount = input("Quantidade ou nível (1 a 64)", prefs.getInt(GameBridgeService.KEY_LIKE_GOAL_ACTION_AMOUNT, 1).toString(), android.text.InputType.TYPE_CLASS_NUMBER)
        root.addView(amount)
        val custom = input("Ex.: summon zombie ~ ~1 ~", prefs.getString(GameBridgeService.KEY_LIKE_GOAL_ACTION_VALUE, "").orEmpty(), android.text.InputType.TYPE_CLASS_TEXT)
        root.addView(custom)
        root.addView(label("A lista inclui mobs, itens, efeitos, clima, horário, XP, teleporte, TNT e outras ações. Também aceita comando personalizado.", 12f, 0xFFBBBBBB.toInt()))
        fun refresh() {
            val selected = actions.getOrNull(action.selectedItemPosition)
            amount.visibility = if (selected?.type == "GIVE" || selected?.type == "EFFECT") View.VISIBLE else View.GONE
            custom.visibility = if (selected?.type == "CUSTOM") View.VISIBLE else View.GONE
        }
        action.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { refresh() }
        }
        refresh()
        root.addView(button("SALVAR META") {
            val count = target.text.toString().toIntOrNull()
            if (count == null || count !in 1..1_000_000) {
                Toast.makeText(this, "Escolha uma meta entre 1 e 1.000.000 likes.", Toast.LENGTH_LONG).show()
                return@button
            }
            val selected = actions.getOrNull(action.selectedItemPosition) ?: actions.first()
            val value = if (selected.type == "CUSTOM") custom.text.toString().trim().removePrefix("/") else selected.value
            if (selected.type == "CUSTOM" && value.isBlank()) {
                Toast.makeText(this, "Digite o comando personalizado.", Toast.LENGTH_SHORT).show()
                return@button
            }
            prefs.edit()
                .putBoolean(FloatingOverlayService.KEY_LIKE_GOAL_ENABLED, true)
                .putInt(GameBridgeService.KEY_LIKE_GOAL_TARGET, count)
                .putString(GameBridgeService.KEY_LIKE_GOAL_ACTION_TYPE, selected.type)
                .putString(GameBridgeService.KEY_LIKE_GOAL_ACTION_VALUE, value)
                .putInt(GameBridgeService.KEY_LIKE_GOAL_ACTION_AMOUNT, amount.text.toString().toIntOrNull()?.coerceIn(1, 64) ?: 1)
                .apply()
            val refreshIntent = Intent(this, FloatingOverlayService::class.java).setAction(FloatingOverlayService.ACTION_REFRESH)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(refreshIntent) else startService(refreshIntent)
            Toast.makeText(this, "Meta salva: $count ❤️ = " + selected.label, Toast.LENGTH_LONG).show()
            finish()
        })
        root.addView(button("VOLTAR") { finish() })
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.BLACK); addView(root) })
    }

    private fun input(hintText: String, value: String, type: Int) = EditText(this).apply {
        hint = hintText; setText(value); inputType = type; setSingleLine(true); textSize = 15f
        setTextColor(Color.WHITE); setHintTextColor(0xFF888888.toInt())
        background = rounded(14, Color.BLACK, Color.WHITE); setPadding(dp(14), dp(12), dp(14), dp(12))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
    }
    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(8), 0, dp(8))
    }
    private fun button(value: String, click: () -> Unit) = Button(this).apply {
        text = value; isAllCaps = false; setTextColor(Color.WHITE); background = rounded(22, Color.BLACK, Color.WHITE)
        setPadding(dp(12), dp(10), dp(12), dp(10)); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
        setOnClickListener { click() }
    }
    private fun rounded(radius: Int, fill: Int, stroke: Int) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(radius).toFloat(); setStroke(dp(1).coerceAtLeast(1), stroke)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
