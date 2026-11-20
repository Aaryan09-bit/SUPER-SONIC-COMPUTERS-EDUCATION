package com.example.data.repository

import android.content.Context
import com.example.data.model.BundledQuestionItem
import com.example.data.model.BundledQuestionSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class BundledSetsRepository(private val appContext: Context) {

    @Volatile
    private var cachedSets: List<BundledQuestionSet>? = null

    suspend fun loadBundledSets(): List<BundledQuestionSet> {
        cachedSets?.let { return it }
        return withContext(Dispatchers.IO) {
            val raw = appContext.assets.open("supersonic_question_sets.json")
                .bufferedReader()
                .use { it.readText() }
            val root = JSONObject(raw)
            val setsArr = root.getJSONArray("sets")
            val result = ArrayList<BundledQuestionSet>(setsArr.length())
            for (i in 0 until setsArr.length()) {
                val obj = setsArr.getJSONObject(i)
                val qArr = obj.getJSONArray("questions")
                val qList = ArrayList<BundledQuestionItem>(qArr.length())
                for (j in 0 until qArr.length()) {
                    val qObj = qArr.getJSONObject(j)
                    val optsArr = qObj.getJSONArray("options")
                    val opts = ArrayList<String>(optsArr.length())
                    for (k in 0 until optsArr.length()) {
                        opts.add(optsArr.getString(k))
                    }
                    qList.add(
                        BundledQuestionItem(
                            number = qObj.optInt("number", j + 1),
                            text = qObj.optString("text", ""),
                            options = opts,
                            correctOption = qObj.optString("correctOption", ""),
                            subject = qObj.optString("subject", "Computer Fundamentals"),
                            difficulty = qObj.optString("difficulty", "Medium")
                        )
                    )
                }
                result.add(
                    BundledQuestionSet(
                        setId = obj.optString("setId", "set_${i + 1}"),
                        setNumber = obj.optInt("setNumber", i + 1),
                        title = obj.optString("title", "Set ${i + 1}"),
                        rangeLabel = obj.optString("rangeLabel", ""),
                        sourceFileName = obj.optString("sourceFileName", "Set_${i + 1}.pdf"),
                        primarySubject = obj.optString("primarySubject", "Computer Fundamentals"),
                        questionCount = obj.optInt("questionCount", qList.size),
                        questions = qList
                    )
                )
            }
            cachedSets = result
            result
        }
    }
}
