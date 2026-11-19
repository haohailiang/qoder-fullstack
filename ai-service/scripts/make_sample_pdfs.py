"""生成验收用示例保险条款 PDF。

仅在需要重新生成 samples/ 下的示例文件时执行，fpdf2 不是运行时依赖：

    ./.venv/Scripts/python.exe -m pip install fpdf2
    ./.venv/Scripts/python.exe scripts/make_sample_pdfs.py

生成的 PDF 为纯文本（可被 pypdf/MinerU 提取），用于联调「上传 → 解析 → 问答」。
"""

from __future__ import annotations

import sys
from pathlib import Path

from fpdf import FPDF

SAMPLES_DIR = Path(__file__).resolve().parents[1] / "samples"

FONT_CANDIDATES = [
    Path("C:/Windows/Fonts/simhei.ttf"),
    Path("C:/Windows/Fonts/simsun.ttc"),
    Path("C:/Windows/Fonts/msyh.ttc"),
    Path("/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc"),
]


def _font_path() -> Path:
    for candidate in FONT_CANDIDATES:
        if candidate.exists():
            return candidate
    raise SystemExit("未找到可用的中文字体，请安装 SimHei/思源黑体后重试")


def _build_pdf(title: str, sections: list[tuple[str, str]]) -> FPDF:
    font = _font_path()
    pdf = FPDF(format="A4")
    pdf.set_auto_page_break(auto=True, margin=18)
    pdf.add_font("cjk", "", str(font))
    pdf.add_page()
    pdf.set_font("cjk", size=18)
    pdf.multi_cell(0, 10, title, new_x="LMARGIN", new_y="NEXT")
    pdf.ln(2)
    for heading, body in sections:
        pdf.set_font("cjk", size=13)
        pdf.multi_cell(0, 9, heading, new_x="LMARGIN", new_y="NEXT")
        pdf.set_font("cjk", size=11)
        pdf.multi_cell(0, 7, body, new_x="LMARGIN", new_y="NEXT")
        pdf.ln(2)
    return pdf


MILLION_MEDICAL = [
    (
        "第一条 保险期间",
        "本保险合同的保险期间为一年，自合同生效日零时起至期满日二十四时止。保险期间届满前三十日，投保人可申请续保，"
        "续保须经保险人审核同意。",
    ),
    (
        "第二条 等待期",
        "自本合同生效之日起三十日为等待期。等待期内被保险人因疾病发生的医疗费用，保险人不承担给付保险金责任；"
        "因意外伤害发生的医疗费用不受等待期限制。续保保单不受等待期限制。",
    ),
    (
        "第三条 保险责任",
        "在保险期间内，被保险人因意外伤害或在等待期后因疾病，在二级及以上公立医院普通部接受治疗的，"
        "对于其实际支出的、符合当地社会医疗保险支付范围的合理且必要的住院医疗费用，"
        "在扣除免赔额后按百分之百的比例给付一般医疗保险金，年度累计限额为人民币二百万元。",
    ),
    (
        "第四条 免赔额",
        "本保险一般医疗保险金的年度免赔额为人民币一万元。被保险人从社会医疗保险、公费医疗等途径获得的补偿，"
        "不计入免赔额累计。",
    ),
    (
        "第五条 责任免除",
        "因下列情形之一导致被保险人支出医疗费用的，保险人不承担给付保险金责任：（一）投保人对被保险人的故意杀害、"
        "故意伤害；（二）被保险人故意犯罪或者抗拒依法采取的刑事强制措施；（三）被保险人自伤、自杀，但被保险人自杀时为无民事行为能力人的除外；"
        "（四）被保险人主动吸食或注射毒品；（五）战争、军事冲突、暴乱或武装叛乱。",
    ),
    (
        "第六条 保险金申请",
        "保险金申请人应在治疗结束后三十日内向保险人提交保险金给付申请书、保险合同凭证、医院出具的诊断证明、"
        "医疗费用原始凭证及费用清单等材料。保险人收到完整材料后三十日内作出核定。",
    ),
    (
        "第七条 如实告知",
        "订立保险合同时，投保人应当如实回答保险人就与被保险人有关情况提出的询问。投保人故意或者因重大过失未履行如实告知义务，"
        "足以影响保险人决定是否同意承保或者提高保险费率的，保险人有权解除合同。",
    ),
]

CRITICAL_ILLNESS = [
    (
        "第一条 保险期间与等待期",
        "本保险合同的保险期间为一年。自合同生效之日起九十日为等待期，等待期内被保险人经医院确诊初次发生本合同约定的重大疾病，"
        "保险人不承担给付保险金责任，并向投保人无息退还已交保险费。",
    ),
    (
        "第二条 重大疾病保险金",
        "被保险人于等待期后经医院确诊初次发生本合同约定的重大疾病，保险人按基本保险金额给付重大疾病保险金，"
        "本合同对该被保险人的保险责任终止。基本保险金额为人民币五十万元。",
    ),
    (
        "第三条 轻症疾病保险金",
        "被保险人于等待期后经医院确诊初次发生本合同约定的轻症疾病，保险人按基本保险金额的百分之三十给付轻症疾病保险金，"
        "每种轻症疾病限给付一次，累计给付以三次为限。",
    ),
    (
        "第四条 豁免保险费",
        "被保险人于等待期后经医院确诊初次发生本合同约定的轻症疾病或重大疾病，保险人自确诊之日起豁免本合同以后各期应交保险费，"
        "本合同继续有效。",
    ),
    (
        "第五条 责任免除",
        "因下列情形之一导致被保险人发生本合同约定的疾病的，保险人不承担给付保险金责任：（一）投保人对被保险人的故意杀害、故意伤害；"
        "（二）被保险人故意自伤、犯罪或者抗拒依法采取的刑事强制措施；（三）被保险人主动吸食或注射毒品；"
        "（四）遗传性疾病、先天性畸形、变形或染色体异常。",
    ),
]


def main() -> None:
    SAMPLES_DIR.mkdir(parents=True, exist_ok=True)
    targets = [
        ("安心百万医疗险条款.pdf", "安心百万医疗险保险条款", MILLION_MEDICAL),
        ("守护重疾险条款.pdf", "守护重疾险保险条款", CRITICAL_ILLNESS),
    ]
    for filename, title, sections in targets:
        pdf = _build_pdf(title, sections)
        target = SAMPLES_DIR / filename
        pdf.output(str(target))
        print(f"generated: {target} ({target.stat().st_size} bytes)")


if __name__ == "__main__":
    sys.exit(main())