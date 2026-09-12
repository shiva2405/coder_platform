INSERT INTO problems (slug, title, description, difficulty, tags, time_limit_ms, memory_limit_bytes)
VALUES
(
    'a-plus-b',
    'A + B',
    $desc$Read two integers A and B from standard input and print their sum.

Input
  A single line containing two integers A and B (−1 000 000 ≤ A, B ≤ 1 000 000).

Output
  A single integer: A + B.$desc$,
    'EASY',
    'math,beginner',
    1000,
    67108864
),
(
    'fizzbuzz',
    'FizzBuzz',
    $desc$Read an integer N and print the FizzBuzz sequence from 1 to N.

For each integer i from 1 to N inclusive, print one line:
  - FizzBuzz if i is divisible by both 3 and 5
  - Fizz if i is divisible by 3
  - Buzz if i is divisible by 5
  - otherwise the number i itself

Input
  A single integer N (1 ≤ N ≤ 200).

Output
  N lines as described above.$desc$,
    'EASY',
    'implementation,beginner',
    2000,
    67108864
),
(
    'palindrome-string',
    'Palindrome String',
    $desc$Read a single line of text and decide whether it is a palindrome.

Compare characters exactly. Do not ignore case, spaces, or punctuation.

Input
  One line of text (1 to 200 characters).

Output
  YES if the line is a palindrome, otherwise NO.$desc$,
    'EASY',
    'strings',
    1000,
    67108864
),
(
    'maximum-of-n',
    'Maximum of N',
    $desc$Find the maximum value in a list of integers.

Input
  The first line contains an integer N (1 ≤ N ≤ 1000).
  The second line contains N integers separated by spaces (−1 000 000 ≤ each ≤ 1 000 000).

Output
  A single integer: the maximum value.$desc$,
    'MEDIUM',
    'arrays,implementation',
    2000,
    67108864
);

INSERT INTO test_cases (problem_id, input_data, expected_output, points, is_sample, sort_order)
SELECT id, '1 2', '3', 10, TRUE, 1 FROM problems WHERE slug = 'a-plus-b';
INSERT INTO test_cases (problem_id, input_data, expected_output, points, is_sample, sort_order)
SELECT id, '10 20', '30', 10, TRUE, 2 FROM problems WHERE slug = 'a-plus-b';
INSERT INTO test_cases (problem_id, input_data, expected_output, points, is_sample, sort_order)
SELECT id, '0 0', '0', 20, FALSE, 3 FROM problems WHERE slug = 'a-plus-b';
INSERT INTO test_cases (problem_id, input_data, expected_output, points, is_sample, sort_order)
SELECT id, '-5 10', '5', 30, FALSE, 4 FROM problems WHERE slug = 'a-plus-b';
INSERT INTO test_cases (problem_id, input_data, expected_output, points, is_sample, sort_order)
SELECT id, '1000000 2000000', '3000000', 30, FALSE, 5 FROM problems WHERE slug = 'a-plus-b';

INSERT INTO test_cases (problem_id, input_data, expected_output, points, is_sample, sort_order)
SELECT id, '5', E'1\n2\nFizz\n4\nBuzz', 15, TRUE, 1 FROM problems WHERE slug = 'fizzbuzz';
INSERT INTO test_cases (problem_id, input_data, expected_output, points, is_sample, sort_order)
SELECT id, '1', '1', 10, FALSE, 2 FROM problems WHERE slug = 'fizzbuzz';
INSERT INTO test_cases (problem_id, input_data, expected_output, points, is_sample, sort_order)
SELECT id, '15', E'1\n2\nFizz\n4\nBuzz\nFizz\n7\n8\nFizz\nBuzz\n11\nFizz\n13\n14\nFizzBuzz', 40, FALSE, 3 FROM problems WHERE slug = 'fizzbuzz';
INSERT INTO test_cases (problem_id, input_data, expected_output, points, is_sample, sort_order)
SELECT id, '3', E'1\n2\nFizz', 35, FALSE, 4 FROM problems WHERE slug = 'fizzbuzz';

INSERT INTO test_cases (problem_id, input_data, expected_output, points, is_sample, sort_order)
SELECT id, 'aba', 'YES', 10, TRUE, 1 FROM problems WHERE slug = 'palindrome-string';
INSERT INTO test_cases (problem_id, input_data, expected_output, points, is_sample, sort_order)
SELECT id, 'abc', 'NO', 10, TRUE, 2 FROM problems WHERE slug = 'palindrome-string';
INSERT INTO test_cases (problem_id, input_data, expected_output, points, is_sample, sort_order)
SELECT id, 'a', 'YES', 20, FALSE, 3 FROM problems WHERE slug = 'palindrome-string';
INSERT INTO test_cases (problem_id, input_data, expected_output, points, is_sample, sort_order)
SELECT id, 'abba', 'YES', 30, FALSE, 4 FROM problems WHERE slug = 'palindrome-string';
INSERT INTO test_cases (problem_id, input_data, expected_output, points, is_sample, sort_order)
SELECT id, 'Abba', 'NO', 30, FALSE, 5 FROM problems WHERE slug = 'palindrome-string';

INSERT INTO test_cases (problem_id, input_data, expected_output, points, is_sample, sort_order)
SELECT id, E'5\n3 1 4 1 5', '5', 15, TRUE, 1 FROM problems WHERE slug = 'maximum-of-n';
INSERT INTO test_cases (problem_id, input_data, expected_output, points, is_sample, sort_order)
SELECT id, E'1\n42', '42', 15, TRUE, 2 FROM problems WHERE slug = 'maximum-of-n';
INSERT INTO test_cases (problem_id, input_data, expected_output, points, is_sample, sort_order)
SELECT id, E'4\n-3 -1 -7 -2', '-1', 35, FALSE, 3 FROM problems WHERE slug = 'maximum-of-n';
INSERT INTO test_cases (problem_id, input_data, expected_output, points, is_sample, sort_order)
SELECT id, E'6\n9 9 1 9 0 8', '9', 35, FALSE, 4 FROM problems WHERE slug = 'maximum-of-n';
