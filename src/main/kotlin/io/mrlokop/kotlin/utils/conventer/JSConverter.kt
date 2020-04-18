package io.mrlokop.kotlin.utils.conventer

import com.google.gson.Gson
import io.mrlokop.kotlin.utils.conventer.enities.EntryEntity
import io.mrlokop.kotlin.utils.conventer.enities.IntPrimitiveEntity
import io.mrlokop.kotlin.utils.conventer.enities.TypeEntity
import io.mrlokop.kotlin.utils.conventer.enities.expression.*
import io.mrlokop.kotlin.utils.conventer.utils.ScriptBuilder
import io.mrlokop.kotlin.utils.conventer.utils.debug

@Deprecated("Rewriting to script builder")
class OldJSConverter(private val entries: List<EntryEntity>) {
    @Deprecated("Use JSConverter")
    fun convert(): String {
        //language=JavaScript
        var script = "\n///\n"
        script += "///\tAutogenerated by KConvertLib\n"
        script += "///\t\tAuthor: vk.com/themrlokopoff\n"
        script += "///\n"
        script += "\n"
        //language=JavaScript
        script += """
            const root = {};
            const ${escapePackage("converter")} = (() => {
                function escapePackage(pck) {
                    return "${'$'}_" + pck.replace(/\./gm, "_")              
                }
                
                function getPackage(key) {
                    let a = root;
                    for (const k of key.split(".")) {
                        const p = a;
                        a = a[k] || {};
                        p[k] = a;
                    }            
                    return a;
                }
                const converterPackage =  getPackage("converter")
                converterPackage.getPackage = getPackage;
                converterPackage.escapePackage = escapePackage;
                return converterPackage;
            })();

        """.trimIndent()
        entries.forEach { entry ->

            script += """
                (() => {
                /* -> AUTOGENERATED ENTRY <- */
                /*    -> PACKAGE INFO <- */
                /* Package: ${entry.packageName} */
                /* File name: ${entry.fileName} */
                
            """
            val packageName = escapePackage(entry.packageName)

            //language=JavaScript
            if (packageName == "\$") {

                script += """
                const $packageName = root

            """.trimIndent()
            } else {
                script += """
                const $packageName = ${escapePackage("converter")}.getPackage("${entry.packageName}")

            """.trimIndent()
            }

            entry.topLevels.forEach {
                it.declarations.forEach {
                    it.fields.forEach {
                        if (it.expression != null) {
                            script += """
                $packageName['${it.name}'] = ${wrap(it.expression!!)};
            """.trimIndent()

                        }
                    }
                    it.functions.forEach {
                        val funcName = packageName + "_" + it.name
                        script += """
                            function $funcName(${'$'}__args) {
                                ${
                        it.body.block.statements.map {
                            return@map it.expressions.map {
                                wrap(it)
                            }.joinToString(";\n")
                        }.joinToString(";\n")
                        }
                            }
                            $funcName.${'$'} = {
                                _meta: {
                                    mods: ${Gson().toJson(it.mods)},
                                    package: $packageName,
                                    packageName: '${entry.packageName}',
                                    parameters: [
                                        ${it.params.map {
                            return@map """
                                {
                                    name: "${it.name}",
                                    type: ${serialize(it.type!!)}
                                }
                            """.trimIndent()
                        }.joinToString(",\n")}
                                    ]
                                }
                            }
                            $packageName['${it.name}'] = $funcName;
                            
            """.trimIndent()


                    }
                }
            }

            script += """
                })();
            """
        }
        script =
            "exports = () => {\n" +
                    script.replace("\n", "\n\t") +
                    """
                        (() => {
                            function recursive(data) {
                        for (const key of Object.keys(data)) {
                        const v = data[key];
                        
                                                        if (typeof v === 'object') {
    recursive(v)                                                        
                                                        }
                                                        if (typeof v === 'function') {
if (key === "main") {
                                                        v()
                                                        }
                                                        
                                                        }
                                                        }
                            }
                            recursive(root)
                        })()
                        return root;
                        }
                        console.log(exports())
                    """.trimIndent()
        //"\n\n\n\treturn root\n}\n\nconsole.log(\"Compiled: \", exports())"


        return script
    }

    fun escapePackage(pck: String): String {
        if (pck.isEmpty())
            return "\$"
        return "\$_" + pck.replace(".", "_")
    }

    fun serialize(data: Any): String {
        if (data is TypeEntity) {
            if (data.subTypes.isEmpty()) {
                return "{\n\"name\": '${data.name}'\n}"
            } else {
                return "{\n\"name\": '${data.name}', \nsubTypes: [\n${data.subTypes.map {
                    return@map serialize(it)
                }.joinToString(", ")}\n]\n}"
            }
        }
        return "/*Serialization failed: ${data.javaClass.name}*/"
    }

    fun wrap(expression: ExpressionEntity): String {

        if (expression is FunctionInvokeExpression) {

            var str =
                (if (expression.member.isNotEmpty()) (expression.member + ".") else "") + expression.functionName + "(...["
            expression.args.forEach {
                str += "\n${wrap(it)},"
            }
            str += "\n])"
            return str

        }
        if (expression is DeclarationExpression) {
            var a: String
            if (expression.field.decType == "val")
                a = "const "
            else
                a = "var "
            a += expression.field.name + " = " + wrap(expression.field.expression!!)
            return a
        }
        if (expression is ConstantExpression) {
            if (expression.const is IntPrimitiveEntity) {
                return "${(expression.const as IntPrimitiveEntity).get()}"
            }
        }
        if (expression is MultiplicativeExpression) {

            var script = " "
            expression.operations.forEach {
                if (it is MultiplicativeData) {
                    script += " (${wrap(it.data)}) "
                }
                if (it is MultiplicativeOperator) {
                    script += it.data
                }
            }
            script += " "
            return script
        }
        if (expression is LambdaExpression) {

            var script = "(/* not supported now */) => {\n"
            expression.statements.forEach {
                it.expressions.forEach {
                    script += wrap(it) + ";\n"
                }
            }
            script += "\n}"
            return script
        }
        if (expression is AdditiveExpression) {
            var script = " "
            expression.operations.forEach {
                if (it is AdditiveData) {
                    script += " (${wrap(it.data)}) "
                }
                if (it is AdditiveOperator) {
                    script += it.data
                }
            }
            script += " "
            return script
        }
        if (expression is StringExpression) {
            return "'${expression.get()}'"
        }
        if (expression is IdentifierExpression) {
            return expression.identifier
        }
        if (expression.javaClass.name == "io.mrlokop.kotlin.utils.conventer.enities.expression.ExpressionEntity") {
            return ""
        }
        return "/*Wrapping failed: ${expression.javaClass.name}*/"
    }
}

class JSConverter(val entries: List<EntryEntity>) {

    var enableExpressionsShow = false
    var enableAutoRun = true

    fun convert(): String {
        var script = ScriptBuilder()
        var entryId = 0

        script + "exports = () => {"
        script++
        script + "const root = {};"
        +script
        includeConverterAPI(script)
        +script
        entries.forEach { entry ->
            entryId++

            script + "// Entry #$entryId"
            script + "// -> File: ${entry.fileName}"
            script + "// -> Package: ${entry.packageName}"
            +script
            script + "(() => {"

            script.wrap {
                +"// Script..."
                +"const " + escapePackage(entry.packageName) + " = root.converter.getPackage('" + entry.packageName + "'); // Get getPackage fun for get package"
                +""
                +""
                +(" // Field declarations")
                +""
                entry.topLevels.forEach {
                    it.declarations.forEach {
                        it.fields.forEach {
                            expr(
                                +(if (it.decType == "var") "var" else "const") + " " + it.name + " = ",
                                it.expression!!
                            )
                            +escapePackage(entry.packageName) + "['" + it.name + "'] = " + it.name
                        }
                    }
                }

                +""
                +(" // Function declarations")
                +""
                entry.topLevels.forEach {
                    it.declarations.forEach {
                        it.functions.forEach {

                            +"/**"
                            +" * Function converted from ${entry.packageName}.${it.name}"
                            +" *"
                            if (it.params.isNotEmpty()) {
                                it.params.forEach {
                                    val line = (+" * @param " + it.name)
                                    if (it.type != null) {
                                        line + " " + serialize(it.type!!)
                                    }
                                }
                            }
                            +" */"
                            val line = (+"function " + it.name + "(")
                            if (it.params.isNotEmpty()) {
                                it.params.forEach {
                                    line + it.name + ", "
                                }
                                line.sub(2)
                            }
                            line + ") {"
                            line.script++
                            line.script + "// body"
                            it.body.block.statements.forEach {
                                it.expressions.forEach {
                                    expr(line.script.ln(), it)
                                }
                            }
                            line.script--
                            line.script + "}"

                            +escapePackage(entry.packageName) + "['" + it.name + "'] = " + it.name
                        }
                    }
                }

            }

            script + "})();"
            +script
        }
        +script
        includeBootstrap(script)
        +script
        script + "return root;"
        script--
        script + "}"
        if (enableAutoRun) {
            +script
            +script
            script + "// Autorun"
            script + "console.log(\"Data:\", exports())"
        }
        return script.toString()
    }

    /** SCRIPT API **/
    private fun includeConverterAPI(script_: ScriptBuilder) {
        var script = script_
        script + "(() => {"
        script++ // 1
        script + "function escapePackage(pck) {"
        script++ // 2
        script + "return \"\$_\" + pck.replace(/\\./gm, \"_\")"
        script-- // 1
        script + "}"
        +script
        script + "function getPackage(key) {"
        script++ // 2
        script + "let a = root;"
        script + "for (const k of key.split(\".\")) {"
        script++ // 3
        script + "const p = a;"
        script + "a = a[k] || {};"
        script + "p[k] = a;"
        script-- // 2
        script + "}"
        script + "return a;"
        script-- // 1
        script + "}"
        +script
        script + "const converterPackage = getPackage(\"converter\")"
        script + "converterPackage.getPackage = getPackage;"
        script + "converterPackage.escapePackage = escapePackage;"
        script-- // 0
        script + "})();"
    }

    private fun includeBootstrap(script_: ScriptBuilder) {
        var script = script_
        script + "function recursive(data) {"
        script++
        script + "for (const key of Object.keys(data)) {"
        script++
        script + "const v = data[key];"
        script + "if (typeof v === 'object') {"
        script++
        script + "recursive(v)"
        script--
        script + "}"
        script + "if (typeof v === 'function') {"
        script++
        script + "if (key === \"main\") {"
        script++
        script + "v()"
        script--
        script + "}"
        script--
        script + "}"
        script--
        script + "}"
        script--
        script + "}"

        +script
        script + "recursive(root)"
    }

    /** UTILS **/
    fun escapePackage(pck: String): String {
        if (pck.isEmpty())
            return "\$"
        return "\$_" + pck.replace(".", "_")
    }

    private fun serialize(data: Any): String {
        when (data) {
            is TypeEntity -> {
                var a = data.name
                if (data.subTypes.isNotEmpty()) {
                    a += "<"
                    data.subTypes.forEach {
                        a += serialize(it) + ","
                    }
                    a = a.substring(0, a.length - 1) + ">"
                }
                return a
            }
        }
        return "/* Fail to serialize (${data.javaClass.simpleName}) */"
    }

    private fun expr(line_: ScriptBuilder.ScriptLine, expression: ExpressionEntity): ScriptBuilder.ScriptLine {
        var line = line_
        if (enableExpressionsShow)
            line + "/* <" + expression.javaClass.simpleName + "> */"
        when (expression) {
            is StringExpression -> {
                line + "'" + expression.get() + "'"
            }
            is DeclarationExpression -> {
                expr(
                    line + (if (expression.field.decType == "var") "var" else "const") + " " + expression.field.name + " = ",
                    expression.field.expression!!
                )
            }
            is IdentifierExpression -> {
                line + expression.identifier
            }
            is FunctionInvokeExpression -> {
                val func =
                    (if (expression.member.isNotEmpty()) (expression.member + ".") else "") + expression.functionName

                line + func + "("
                if (expression.args.isNotEmpty()) {
                    for (arg in expression.args) {
                        line = expr(line, arg) + ","
                    }
                    line.sub(1) + ");"
                } else {
                    line + ")"
                }
            }

            is MultiplicativeExpression -> {

                expression.operations.forEach {
                    if (it is MultiplicativeData) {
                        expr(line + " ( ", it.data) + " ) "
                    }
                    if (it is MultiplicativeOperator) {
                        line + it.data
                    }
                }
            }
            is ConstantExpression -> {
                when (expression.const) {
                    is IntPrimitiveEntity -> {
                        line + (expression.const as IntPrimitiveEntity).get().toString()
                    }
                    else -> {
                        line + "/* Failed to unwrap ConstantExpression (${expression.const!!.javaClass.simpleName}) */"
                    }
                }
            }
            is AdditiveExpression -> {
                expression.operations.forEach {
                    if (it is AdditiveData) {
                        expr(line + " ( ", it.data) + " ) "
                    }
                    if (it is AdditiveOperator) {
                        line + it.data
                    }
                }
            }
            is LambdaExpression -> {
                var script = (line + "() => {").script
                script++
                script + "// lambda"
                expression.statements.forEach {
                    it.expressions.forEach {
                        expr(script.ln(), it)
                    }
                }
                script--
                script + "}"

            }
            else -> {
                if (expression.javaClass.name != "io.mrlokop.kotlin.utils.conventer.enities.expression.ExpressionEntity") {
                    println()
                    debug()
                    debug("Failed serialize")
                    debug("-> ${expression.javaClass.name}")
                    debug()
                    println()
                    line + " /* Failed to serialize expression ${expression.javaClass.simpleName} */ "
                } else {
                    line.remove()
                }
            }
        }
        if (enableExpressionsShow)
            line + "/* </" + expression.javaClass.simpleName + "> */"
        return line
    }
}
