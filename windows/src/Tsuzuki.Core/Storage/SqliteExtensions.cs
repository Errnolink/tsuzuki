using Microsoft.Data.Sqlite;

namespace Tsuzuki.Core.Storage;

internal static class SqliteExtensions
{
    public static int ExecuteNonQuery(this SqliteConnection db, string sql, params (string Name, object? Value)[] parameters)
    {
        using var cmd = db.CreateCommand();
        cmd.CommandText = sql;
        Bind(cmd, parameters);
        return cmd.ExecuteNonQuery();
    }

    public static object? Scalar(this SqliteConnection db, string sql, params (string Name, object? Value)[] parameters)
    {
        using var cmd = db.CreateCommand();
        cmd.CommandText = sql;
        Bind(cmd, parameters);
        return cmd.ExecuteScalar();
    }

    public static T? Scalar<T>(this SqliteConnection db, string sql, params (string Name, object? Value)[] parameters)
    {
        var value = db.Scalar(sql, parameters);
        if (value is null || value is DBNull) return default;
        var targetType = Nullable.GetUnderlyingType(typeof(T)) ?? typeof(T);
        return (T)Convert.ChangeType(value, targetType);
    }

    public static List<SqliteRow> Query(this SqliteConnection db, string sql, params (string Name, object? Value)[] parameters)
    {
        using var cmd = db.CreateCommand();
        cmd.CommandText = sql;
        Bind(cmd, parameters);
        using var reader = cmd.ExecuteReader();
        var columns = Enumerable.Range(0, reader.FieldCount).Select(reader.GetName).ToArray();
        var rows = new List<SqliteRow>();
        while (reader.Read())
        {
            var row = new SqliteRow();
            for (var i = 0; i < columns.Length; i++)
            {
                row[columns[i]] = reader.IsDBNull(i) ? null : reader.GetValue(i);
            }
            rows.Add(row);
        }
        return rows;
    }

    public static void QueryEach(this SqliteConnection db, string sql, Action<SqliteDataReader> row, params (string Name, object? Value)[] parameters)
    {
        using var cmd = db.CreateCommand();
        cmd.CommandText = sql;
        Bind(cmd, parameters);
        using var reader = cmd.ExecuteReader();
        while (reader.Read())
        {
            row(reader);
        }
    }

    private static void Bind(SqliteCommand cmd, (string Name, object? Value)[] parameters)
    {
        foreach (var (name, value) in parameters)
        {
            var p = cmd.CreateParameter();
            p.ParameterName = name;
            p.Value = value ?? DBNull.Value;
            cmd.Parameters.Add(p);
        }
    }
}

internal sealed class SqliteRow
{
    private readonly Dictionary<string, object?> _values = new(StringComparer.OrdinalIgnoreCase);

    public object? this[string column]
    {
        get => _values.GetValueOrDefault(column);
        set => _values[column] = value;
    }

    public long Long(string column) => _values[column] is null ? 0 : Convert.ToInt64(_values[column]);
    public long? LongOrNull(string column) => _values[column] is null ? null : Convert.ToInt64(_values[column]);
    public double Double(string column) => _values[column] is null ? 0 : Convert.ToDouble(_values[column]);
    public string Text(string column) => _values[column] as string ?? "";
    public string? TextOrNull(string column) => _values[column] as string;
    public byte[] Blob(string column) => _values[column] as byte[] ?? Array.Empty<byte>();
    public bool Bool(string column) => Long(column) != 0;
}
