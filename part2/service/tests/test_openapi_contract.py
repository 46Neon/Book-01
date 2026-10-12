from pathlib import Path

from openapi_spec_validator import validate
from openapi_spec_validator.readers import read_from_filename


def test_part2a_accounting_contract_is_valid_openapi():
    repository_root = Path(__file__).resolve().parents[3]
    contract = repository_root / "docs" / "parte-2a-openapi-propuesta.yaml"
    spec, _ = read_from_filename(str(contract))

    validate(spec)

    assert spec["openapi"].startswith("3.1.")
    assert set(spec["paths"]) == {
        "/books/{bookId}/accounts",
        "/books/{bookId}/journal-entries",
        "/books/{bookId}/accounts/{accountId}/balance",
        "/books/{bookId}/reports/trial-balance",
        "/books/{bookId}/reports/income-statement",
    }
    assert all(set(path_item) == {"get"} for path_item in spec["paths"].values())
