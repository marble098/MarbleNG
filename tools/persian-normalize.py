#!/usr/bin/env python3
"""MARBLE_PERSIAN_JOIN_V249 — the build-time twin of `PersianOrthography`.

The Kotlin object owns the rule at runtime (every translated string passes through it). This sweep
owns the same rule over the source, so the literals the app ships are already correct before they
are ever drawn, and a reviewer can re-run it to prove nothing new drifted in.

    python3 tools/persian-normalize.py            # report what would change
    python3 tools/persian-normalize.py --write     # apply it

The scan works on *Persian runs* — maximal sequences of Persian letters and half-spaces — not on
whitespace tokens, so a glued plural inside `assertEquals("آمادهای؟", …)` is found exactly where
the Kotlin scanner would find it. `PersianOrthography.kt` itself is skipped: its tables carry the
wrong spellings on purpose, as the patterns the rule looks for.
"""

import collections
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ZW = "\u200c"

# Letters only: the Arabic comma (،), semicolon (؛) and question mark (؟) live inside 0600–06FF
# too, and counting one as a letter hides the suffix behind it from the rule.
LETTER = "\u0621-\u064a\u066e-\u06d3\u06d5"
RUN = re.compile(f"[{LETTER}{ZW}]+")

JOINING = set("بپتثجچحخسشصضطظعغفقکگلمنهیئؤ")

NEVER_SPLIT = {
    "تنها", "تنهای", "تنهایی", "تنهاست", "تنهایم", "تنهایت",
    "نهایی",
    "گیتهاب", "گیتهابی", "گیتهابه",
    "شما", "شماها", "چهارها", "خواها", "ماهها",
}

COMPOUND_STEMS = {
    "راهاندازی": f"راه{ZW}اندازی",
    "جابهجایی": f"جابه{ZW}جایی",
    "بهروزرسانی": f"به{ZW}روزرسانی",
    "پیشتنظیم": f"پیش{ZW}تنظیم",
    "دستدهی": f"دست{ZW}دهی",
    "پینشده": f"پین{ZW}شده",
    "تایماوت": f"تایم{ZW}اوت",
    "میانبر": f"میان{ZW}بر",
    "عیبیابی": f"عیب{ZW}یابی",
    "بهصورت": f"به{ZW}صورت",
    "پیشفرض": f"پیش{ZW}فرض",
    "تیکخورده": f"تیک{ZW}خورده",
    "رمزنگاریشده": f"رمزنگاری{ZW}شده",
    "رانتهام": f"ران{ZW}تایم",
    "گیتهابمون": f"گیتهاب{ZW}مون",
}

INDEFINITE_STEMS = {
    "استفادهای": f"استفاده{ZW}ای",
    "تاریخچهای": f"تاریخچه{ZW}ای",
    "تازهای": f"تازه{ZW}ای",
    "دامنهای": f"دامنه{ZW}ای",
    "لحظهای": f"لحظه{ZW}ای",
    "مرحلهای": f"مرحله{ZW}ای",
    "چندمرحلهای": f"چندمرحله{ZW}ای",
    "پینشدهای": f"پین{ZW}شده{ZW}ای",
    "آمادهای": f"آماده{ZW}ای",
    "نسخهای": f"نسخه{ZW}ای",
    "خانهای": f"خانه{ZW}ای",
    "نمونهای": f"نمونه{ZW}ای",
    "صفحهای": f"صفحه{ZW}ای",
    "جملهای": f"جمله{ZW}ای",
    "پایهای": f"پایه{ZW}ای",
    "قاعدهای": f"قاعده{ZW}ای",
    "دادهای": f"داده{ZW}ای",
}

MI_VERBS = {
    "میکنم", "میکنی", "میکنه", "میکنیم", "میکنید", "میکنن",
    "میشه", "میشی", "میشیم", "میشن",
    "میمونه", "میمونن", "میمونی", "میمونم",
    "میره", "میرن", "میری", "میریم", "میرم",
    "میده", "میدن", "میدی", "میدیم", "میدم",
    "میخواد", "میخوان", "میخوام", "میخوای",
    "میگیره", "میگیرن", "میگیری", "میگیریم",
    "میگه", "میگن", "میگی", "میگیم",
    "میفرسته", "میفرستن", "میفهمه", "میفهمی",
    "میاد", "میوفته", "میافته", "میبره", "میبرن",
    "میتونه", "میتونن", "میتونم", "میتونی",
    "میخونه", "میخونن", "میبندم", "میبنده", "میبندن",
}

PLURAL_SUFFIXES = ["هایشان", "هایی", "هاست", "های", "ها"]

TARGETS = ("app/src/**/*.kt", "app/src/**/*.xml")
# Two files carry the *wrong* spelling on purpose, and rewriting them would delete the thing they
# exist to prove: the normalizer's own tables (left column is the glued form it must recognize) and
# its test (the input of every assertion is glued, the expected output is not).
SKIPPED = {
    "app/src/main/java/com/marbleng/app/model/PersianOrthography.kt",
    "app/src/test/java/com/marbleng/app/model/PersianOrthographyV249Test.kt",
}


def separate_plural(word: str) -> str:
    out = word
    cursor = 0
    changed = False
    while cursor < len(out):
        suffix = next((s for s in PLURAL_SUFFIXES if out.startswith(s, cursor)), None)
        if suffix is None:
            cursor += 1
            continue
        after = cursor + len(suffix)
        tail = out[after] if after < len(out) else None
        behind = out[cursor - 1] if cursor > 0 else " "
        joins_behind = behind in JOINING and behind != ZW
        closed = tail is None or tail not in JOINING and not RUN.fullmatch(tail)
        stem = out[:cursor]
        if joins_behind and closed and stem not in NEVER_SPLIT:
            out = out[:cursor] + ZW + out[cursor:]
            cursor += 1 + len(suffix)
            changed = True
        else:
            cursor = after
    return out if changed else word


def separate_verb_prefix(word: str) -> str:
    if not word.startswith("می") or len(word) <= 2:
        return word
    if word in MI_VERBS:
        return "می" + ZW + word[2:]
    return word


def normalize_word(word: str) -> str:
    if not word or word in NEVER_SPLIT:
        return word
    out = INDEFINITE_STEMS.get(word, word)
    for glued, joined in COMPOUND_STEMS.items():
        if glued in out:
            out = out.replace(glued, joined)
    out = separate_plural(out)
    return separate_verb_prefix(out)


def normalize(text: str) -> str:
    return RUN.sub(lambda m: normalize_word(m.group(0)), text)


def main() -> int:
    write = "--write" in sys.argv
    changes = collections.Counter()
    touched = []
    for pattern in TARGETS:
        for path in sorted(p for p in ROOT.glob(pattern) if p.is_file()):
            relative = path.relative_to(ROOT).as_posix()
            if relative in SKIPPED:
                continue
            text = path.read_text(encoding="utf-8")
            fixed = normalize(text)
            if fixed == text:
                continue
            touched.append(relative)
            for run in set(RUN.findall(text)):
                after = normalize_word(run)
                if after != run:
                    changes[(run, after)] += text.count(run)
            if write:
                path.write_text(fixed, encoding="utf-8")
    verb = "rewrote" if write else "would rewrite"
    print(f"{verb} {len(touched)} file(s); {sum(changes.values())} glued word(s)")
    for name in touched:
        print("  *", name)
    for (before, after), count in sorted(changes.items()):
        print(f"  {count:3d}  {before}  ->  {after}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
