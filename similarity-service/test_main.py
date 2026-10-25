"""Check title ranking without loading an ML model or starting a server."""

import importlib
import sys
import unittest
from unittest.mock import Mock, patch


class SimilarityTests(unittest.TestCase):
    def test_scores_stay_with_their_titles_and_are_sorted_highest_first(self):
        model = Mock()
        model.encode.return_value = ["candidate-vector", "first-vector", "second-vector"]
        transformers = Mock()
        transformers.SentenceTransformer.return_value = model
        transformers.util.cos_sim.return_value = [[0.412345, 0.951234]]

        # main.py loads the model on import, so supply the mocks before importing it.
        with patch.dict(sys.modules, {"sentence_transformers": transformers, "torch": Mock()}):
            sys.modules.pop("main", None)
            service = importlib.import_module("main")
            request = service.SimilarityRequest(
                candidate="New theory", existingTitles=["First", "Second"]
            )

            result = service.check_similarity(request)

            self.assertEqual(result.results[0].title, "Second")
            self.assertEqual(result.results[0].score, 0.9512)
            self.assertEqual(result.results[1].title, "First")
            self.assertEqual(result.results[1].score, 0.4123)
            self.assertEqual(len(result.results), 2)
            model.encode.assert_called_once_with(
                ["New theory", "First", "Second"], convert_to_tensor=True
            )
            transformers.util.cos_sim.assert_called_once_with(
                "candidate-vector", ["first-vector", "second-vector"]
            )


if __name__ == "__main__":
    unittest.main()
