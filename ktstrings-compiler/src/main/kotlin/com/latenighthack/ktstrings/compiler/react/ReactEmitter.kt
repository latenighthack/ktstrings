package com.latenighthack.ktstrings.compiler.react

import com.latenighthack.ktstrings.compiler.ArgumentType
import com.latenighthack.ktstrings.compiler.Body
import com.latenighthack.ktstrings.compiler.CatalogEmitter
import com.latenighthack.ktstrings.compiler.CompiledCatalog
import com.latenighthack.ktstrings.compiler.GenerationOptions
import com.latenighthack.ktstrings.compiler.Message
import com.latenighthack.ktstrings.compiler.Names
import com.latenighthack.ktstrings.compiler.Token
import java.nio.file.Files
import java.nio.file.Path

class ReactEmitter : CatalogEmitter {
    override val name = "react"

    override fun emit(
        catalog: CompiledCatalog,
        options: GenerationOptions,
        outputDirectory: Path,
    ) {
        val packageName = options.reactPackageName ?: return
        val source = catalog.source
        val root = outputDirectory.resolve("react")
        Files.createDirectories(root.resolve("locales"))
        Files.createDirectories(root.resolve("metadata"))

        fun write(
            path: String,
            value: String,
        ) {
            Files.writeString(root.resolve(path), value + "\n")
        }

        val locales = linkedMapOf(source.sourceLocale to source.messages.mapValues { it.value.body })
        catalog.translations.forEach { (locale, translated) -> locales[locale] = translated.messages }
        val literalTexts =
            locales.values
                .flatMap { it.values }
                .flatMap { body ->
                    when (body) {
                        is Body.Text -> body.tokens
                        is Body.Plural -> body.cases.values.flatten()
                    }
                }.filterIsInstance<Token.Literal>()
                .map { it.value }
        var delimiter = 0

        while (literalTexts.any { "__KT${delimiter}_BEGIN__" in it || "__KT${delimiter}_END__" in it }) delimiter++
        val prefix = "__KT${delimiter}_BEGIN__"
        val suffix = "__KT${delimiter}_END__"

        fun render(
            tokens: List<Token>,
            message: Message,
        ) = tokens.joinToString("") { token ->
            when (token) {
                is Token.Literal -> token.value
                is Token.Placeholder -> prefix + "a" + message.arguments.indexOfFirst { it.name == token.name } + suffix
            }
        }

        val resources = linkedMapOf<String, Map<String, String>>()
        locales.forEach { (locale, bodies) ->
            val values = linkedMapOf<String, String>()
            bodies.forEach { (id, body) ->
                val message = source.messages.getValue(id)
                when (body) {
                    is Body.Text -> values[id] = render(body.tokens, message)
                    is Body.Plural -> body.cases.forEach { (category, tokens) -> values["${id}_$category"] = render(tokens, message) }
                }
            }
            resources[locale] = values
            write("locales/$locale.json", json(values))
            write("locales/$locale.js", "export default ${json(values)};")
        }

        val contracts =
            source.messages.mapValues { (_, m) ->
                mapOf(
                    "arguments" to m.arguments.map { mapOf("name" to it.name, "type" to it.type.name.lowercase()) },
                    "selector" to (m.body as? Body.Plural)?.selector,
                )
            }
        val availability = source.messages.keys.associateWith { id -> locales.filterValues { id in it }.keys.toList() }
        val metadata =
            mapOf(
                "namespace" to source.namespace,
                "sourceLocale" to source.sourceLocale,
                "cldrVersion" to catalog.cldrVersion,
                "availability" to availability,
                "contracts" to contracts,
                "prefix" to prefix,
                "suffix" to suffix,
            )
        write("metadata/catalog.json", json(metadata))
        val imports = resources.keys.mapIndexed { i, locale -> "import l$i from './locales/$locale.js';" }.joinToString("\n")
        val resourceJs = resources.keys.mapIndexed { i, locale -> "${quote(locale)}: { [namespace]: l$i }" }.joinToString(",")
        val constructors =
            source.messages.values.joinToString(
                ",\n",
            ) { m -> "${quote(apiName(m.id))}: (args = {}) => construct(${quote(m.id)}, args)" }
        write(
            "index.js",
            """
$imports
const metadata = ${json(metadata)};
export const namespace = metadata.namespace;
export const resources = {$resourceJs};
export const catalogMetadata = metadata;
const own = (object, key) => Object.prototype.hasOwnProperty.call(object, key);
function construct(id, args) {
  const contract = metadata.contracts[id];
  if (!args || typeof args !== 'object' || Array.isArray(args)) throw new TypeError('Expected message arguments');
  if (Object.keys(args).length !== contract.arguments.length) throw new TypeError('Unexpected message arguments');
  const values = Object.create(null);
  for (const argument of contract.arguments) {
    if (!own(args, argument.name)) throw new TypeError('Missing message argument');
    const value = args[argument.name];
    if (argument.type === 'string' ? typeof value !== 'string' : !Number.isInteger(value) || value < -2147483648 || value > 2147483647) throw new TypeError('Invalid message argument');
    if (contract.selector === argument.name && value < 0) throw new RangeError('Negative plural quantity');
    values[argument.name] = value;
  }
  return Object.freeze({kind:'message', namespace, id, arguments:Object.freeze(values)});
}
export const messages = Object.freeze({$constructors});
export function literal(text) {
  if (typeof text !== 'string') throw new TypeError('Expected literal string');
  return Object.freeze({kind:'literal',text});
}
export function decodeText(input, generic, diagnostic) {
  const fallback = () => input && typeof input.fallbackText === 'string' ? literal(input.fallbackText) : generic;
  if (!input || typeof input !== 'object') { diagnostic?.({code:'INVALID_TEXT'}); return fallback(); }
  if (input.kind === 'literal' && typeof input.text === 'string') return literal(input.text);
  let code = 'INVALID_TEXT';
  if (input.kind === 'message') {
    code = input.namespace !== namespace ? 'UNKNOWN_NAMESPACE' : !own(metadata.contracts,input.id) ? 'UNKNOWN_MESSAGE' : 'INVALID_ARGUMENTS';
    if (code === 'INVALID_ARGUMENTS') { try { return construct(input.id,input.arguments); } catch {} }
  }
  diagnostic?.({code, namespace: typeof input.namespace === 'string' ? input.namespace : undefined, id: typeof input.id === 'string' ? input.id : undefined});
  return fallback();
}
export function registerKtstrings(instance) {
  const apply = () => { for (const [language, catalog] of Object.entries(resources)) instance.addResourceBundle(language,namespace,catalog[namespace],true,true); };
  if (typeof instance.addResourceBundle === 'function') apply();
  else { const listener = () => { instance.off('initialized',listener); apply(); }; instance.on('initialized',listener); }
  return instance;
}
function selectLocale(id, requested) {
  const available = metadata.availability[id];
  let locale;
  try { locale = Intl.getCanonicalLocales(requested.trim().replaceAll('_','-'))[0]; } catch { locale = metadata.sourceLocale; }
  while (locale) {
    const match = available.find(value => value.toLowerCase() === locale.toLowerCase());
    if (match) return match;
    const separator = locale.lastIndexOf('-');
    locale = separator < 0 ? '' : locale.slice(0,separator);
  }
  return metadata.sourceLocale;
}
export function resolveText(instance, value, requestedLocale) {
  if (value.kind === 'literal') return value.text;
  if (value.namespace !== namespace || !own(metadata.contracts,value.id)) throw new TypeError('Unknown local message');
  const checked = construct(value.id,value.arguments);
  const contract = metadata.contracts[value.id];
  const language = selectLocale(value.id,requestedLocale || instance.language || metadata.sourceLocale);
  const options = {lng:language,lngs:[language],ns:namespace,keySeparator:false,nsSeparator:false,fallbackLng:false,joinArrays:false,returnObjects:false,postProcess:[],applyPostProcessor:false,skipInterpolation:false,nest:false,interpolation:{prefix:metadata.prefix,suffix:metadata.suffix,escapeValue:false,skipOnVariables:true}};
  contract.arguments.forEach((argument,index) => { options['a'+index] = checked.arguments[argument.name]; });
  if (contract.selector) options.count = checked.arguments[contract.selector];
  return instance.t(value.id,options);
}
            """.trimIndent(),
        )
        val types =
            source.messages.values.joinToString("\n") { m ->
                val args =
                    m.arguments.joinToString(
                        "; ",
                    ) { "readonly ${it.name}: ${if (it.type == ArgumentType.STRING) "string" else "number"}" }
                "export type ${apiName(
                    m.id,
                ).replaceFirstChar { it.uppercase() }}Message = { readonly kind: 'message'; readonly namespace: ${quote(
                    source.namespace,
                )}; readonly id: ${quote(m.id)}; readonly arguments: { $args } };"
            }
        val union =
            source.messages.values
                .joinToString(" | ") {
                    apiName(it.id).replaceFirstChar { c ->
                        c.uppercase()
                    } + "Message"
                }.ifEmpty { "never" }
        val factories =
            source.messages.values.joinToString("\n") { m ->
                val type =
                    apiName(m.id).replaceFirstChar { it.uppercase() } + "Message"
                "  ${apiName(m.id)}(${if (m.arguments.isEmpty()) "args?: Record<string, never>" else "args: $type['arguments']"}): $type;"
            }
        write(
            "index.d.ts",
            """
import type { i18n } from 'i18next';
$types
export type LocalMessage = $union;
export type LiteralText = { readonly kind: 'literal'; readonly text: string };
export type UiText = LocalMessage | LiteralText;
export declare const messages: { $factories };
export declare function literal(text: string): LiteralText;
export declare function decodeText(input: unknown, generic: UiText, diagnostic?: (error: {code: string; namespace?: string; id?: string}) => void): UiText;
export declare function registerKtstrings(instance: i18n): i18n;
export declare function resolveText(instance: i18n, value: UiText, requestedLocale?: string): string;
export declare const namespace: string;
export declare const resources: Record<string, Record<string, Record<string, string>>>;
export declare const catalogMetadata: Readonly<Record<string, unknown>>;
            """.trimIndent(),
        )
        write(
            "react.js",
            """
import { useTranslation } from 'react-i18next';
import { namespace, resolveText } from './index.js';
export function useKtstrings(requestedLocale) {
  const {i18n, ready} = useTranslation(namespace);
  return {text: value => resolveText(i18n,value,requestedLocale), i18n, ready};
}
            """.trimIndent(),
        )
        write(
            "react.d.ts",
            """
import type { i18n } from 'i18next';
import type { UiText } from './index.js';
export declare function useKtstrings(requestedLocale?: string): { text(value: UiText): string; i18n: i18n; ready: boolean };
            """.trimIndent(),
        )
        write(
            "package.json",
            json(
                mapOf(
                    "name" to packageName,
                    "version" to options.reactPackageVersion,
                    "type" to "module",
                    "files" to listOf("index.js", "index.d.ts", "react.js", "react.d.ts", "locales", "metadata"),
                    "exports" to
                        mapOf(
                            "." to mapOf("types" to "./index.d.ts", "import" to "./index.js"),
                            "./react" to mapOf("types" to "./react.d.ts", "import" to "./react.js"),
                            "./locales/*" to "./locales/*.json",
                            "./locales/*.json" to "./locales/*.json",
                            "./locales/*.js" to "./locales/*.js",
                            "./metadata/catalog.json" to "./metadata/catalog.json",
                        ),
                    "peerDependencies" to
                        mapOf(
                            "i18next" to ">=25 <27",
                            "react" to ">=18 <20",
                            "react-i18next" to ">=15 <17",
                        ),
                    "peerDependenciesMeta" to
                        mapOf("react" to mapOf("optional" to true), "react-i18next" to mapOf("optional" to true)),
                ),
            ),
        )
    }
}

internal fun apiName(id: String) = Names.camel(id)

internal fun quote(value: String): String =
    buildString {
        append('"')
        value.forEach { c ->
            when (c) {
                '"' -> {
                    append("\\\"")
                }

                '\\' -> {
                    append("\\\\")
                }

                '\n' -> {
                    append("\\n")
                }

                '\r' -> {
                    append("\\r")
                }

                '\t' -> {
                    append("\\t")
                }

                else -> {
                    if (c.code <
                        32
                    ) {
                        append("\\u%04x".format(c.code))
                    } else {
                        append(c)
                    }
                }
            }
        }
        append('"')
    }

internal fun json(value: Any?): String =
    when (value) {
        null -> {
            "null"
        }

        is String -> {
            quote(value)
        }

        is Number, is Boolean -> {
            value.toString()
        }

        is Map<*, *> -> {
            value.entries.joinToString(",", "{", "}") {
                quote(it.key.toString()) +
                    ":" +
                    json(it.value)
            }
        }

        is Iterable<*> -> {
            value.joinToString(",", "[", "]") { json(it) }
        }

        else -> {
            error("Unsupported JSON value")
        }
    }
