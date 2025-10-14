import re

# Requests for personal buy/sell/allocation advice. Research questions about documents are fine.
_ADVICE = re.compile(
    r"\b(should|shall|must) i (buy|sell|invest|hold|short)\b"
    r"|\bis (it|now) a good (time|idea) to (buy|sell|invest)\b"
    r"|\b(what|which) (stock|stocks|shares|share) should i\b"
    r"|\b(recommend|suggest)\b.*\b(buy|sell|invest|portfolio|allocation)\b"
    r"|\bhow much should i (invest|put)\b",
    re.IGNORECASE,
)


def is_personal_advice(question: str) -> bool:
    return bool(_ADVICE.search(question))
