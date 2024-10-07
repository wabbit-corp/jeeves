package jeeves

import lang.mu.std.Mu
import lang.mu.std.MuLiteralString
import lang.mu.std.Upcast

class ConfigBuilder {
    var userAgent: String? = null
    var openaiKey: String? = null
    var discordToken: String? = null
    var imgflipUsername: String? = null
    var imgflipPassword: String? = null
    val personalities = mutableMapOf<String, AgentDescription>()

    var braveKey: String? = null
    var scraperApiKey: String? = null
    var youtubeKey: String? = null
    var kagiToken: String? = null

    val superusers = mutableSetOf<String>()

    @Mu.Export("user-agent") fun userAgent(@Mu.Name("arg") arg: String) {
        userAgent = arg
    }

    @Mu.Export("superuser") fun superuser(@Mu.Name("name") name: String) {
        superusers.add(name)
    }

    @Mu.Export("new-agent") fun newAgent(
        @Mu.Name("name") name: String,
        @Mu.Name("responds-to") @Mu.OneOrMore respondsTo: List<String>,
        @Mu.Name("image-path") @Mu.Optional imagePath: String?,
        @Mu.Name("short-description") shortDescription: String,
        @Mu.Name("personality-prompt") personalityPrompt: String,
        @Mu.Name("physical-appearance") @Mu.Optional physicalAppearance: String?
    ) {
        personalities[name] = AgentDescription(
            name.trim(),
            imagePath?.trim(),
            respondsTo.map { it.trim() },
            shortDescription.trimIndent(),
            personalityPrompt.trimIndent(),
            physicalAppearance?.trimIndent())
    }

    @Mu.Export("openai-key") fun openaiKey(@Mu.Name("key") key: String) {
        openaiKey = key
    }

    @Mu.Export("discord-token") fun discordToken(@Mu.Name("token") token: String) {
        discordToken = token
    }

    @Mu.Export("imgflip-credentials") fun imgflipCredentials(@Mu.Name("username") username: String, @Mu.Name("password") password: String) {
        imgflipUsername = username
        imgflipPassword = password
    }

    @Mu.Export("brave-key") fun braveKey(@Mu.Name("key") key: String) {
        braveKey = key
    }

    @Mu.Export("scraper-api-key") fun scraperApiKey(@Mu.Name("key") key: String) {
        scraperApiKey = key
    }

    @Mu.Export("youtube-key") fun youtubeKey(@Mu.Name("key") key: String) {
        youtubeKey = key
    }

    @Mu.Export("kagi-token") fun kagiToken(@Mu.Name("token") token: String) {
        kagiToken = token
    }

    @Mu.Instance fun <A> upcastNullable(): Upcast<A, A?> = Upcast.of { it }
    @Mu.Instance fun <V> castStringToString() = Upcast.of<MuLiteralString, String> {
        it.value
    }
    @Mu.Instance fun <A1, A2> castList(a: Upcast<A1, A2>): Upcast<List<A1>, List<A2>> =
        Upcast.of { it.map(a::upcast) }

    @Mu.Instance fun <A> upcastIdentity(): Upcast<A, A> =
        Upcast.of { it }
    @Mu.Instance fun <A, B, C> upcastCompose(a2b: Upcast<A, B>, b2c: Upcast<B, C>): Upcast<A, C> =
        Upcast.of { b2c.upcast(a2b.upcast(it)) }

//    @Mu.Export("define") fun define(@Mu.Quoted params: MuExpr, @Mu.Quoted body: MuExpr): Mu.IO<Unit> {
//        TODO()
//    }

//    @Mu.Export(":") fun <A, B> makePair(a: A, b: B): Pair<A, B> = Pair(a, b)
//    @Mu.Export("set-of") fun <A> makeSet(@Mu.ZeroOrMore values: List<A>): Set<A> = values.toSet()
//    @Mu.Export("list-of") fun <A> makeList(@Mu.ZeroOrMore values: List<A>): List<A> = values
//    @Mu.Export("map-of") fun <A, B> makeMap(@Mu.ZeroOrMore values: List<Pair<A, B>>): Map<A, B> = values.toMap()
}
