package com.basic.JWTSecurity.safety;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public page with the rules for what people post and share in Artistry. */
@RestController
public class CommunityRulesApi {

    private static final String PAGE = """
            <!doctype html>
            <html lang="en"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Community Rules - Artistry</title>
            <style>body{font-family:system-ui,sans-serif;max-width:720px;margin:2rem auto;padding:0 1rem;line-height:1.55;color:#222}h1{font-size:1.6rem}h2{font-size:1.15rem;margin-top:1.6rem}</style>
            </head><body>
            <h1>Artistry Community Rules</h1>
            <p>Artistry is a place to enjoy and share art. These rules apply to everything you upload, write, send or say in the app, including artworks, comments, profiles, messages and calls. By using Artistry you agree to follow them.</p>
            <h2>What is not allowed</h2>
            <ul>
            <li>Sexually explicit content, and nude or sexually suggestive uploads. Classic artworks that we show from museum collections are part of our catalogue and are not user uploads.</li>
            <li>Any sexual content involving children, or anything that puts a child at risk.</li>
            <li>Harassment, bullying, threats, stalking, or repeated unwanted contact.</li>
            <li>Hate speech, or attacks on people for who they are.</li>
            <li>Graphic violence, or content that encourages hurting people or yourself.</li>
            <li>Spam, scams, or advertising unrelated to art.</li>
            <li>Passing off someone else's art as your own, or posting work you have no right to share.</li>
            <li>Impersonating another person, or breaking the law.</li>
            </ul>
            <h2>Report and block</h2>
            <p>You can report an artwork, a comment, a person, a chat message or a call from the menu next to it. You can block a person from their profile, a comment or a chat. When you block someone, you stop seeing each other's content, and neither of you can message or call the other. You can undo this in Settings under Blocked users.</p>
            <h2>What we do about reports</h2>
            <p>We review reports and act on them. Content that several different people report is hidden until we have looked at it. Content reported for child safety is hidden straight away. We may remove content, and we may suspend or remove accounts that break these rules. Reports about child sexual abuse material are passed to the authorities.</p>
            <h2>Child safety</h2>
            <p>Artistry does not allow child sexual abuse and exploitation. If you see it, report it in the app or write to us at the address below.</p>
            <h2>Contact</h2>
            <p>art.social, developer of Artistry: <a href="mailto:utkarshxf@gmail.com">utkarshxf@gmail.com</a></p>
            <p>See also our <a href="https://artistryapp.web.app/privacy">Privacy Policy</a>.</p>
            </body></html>
            """;

    @GetMapping(value = "/community-rules", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> rules() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(PAGE);
    }
}
